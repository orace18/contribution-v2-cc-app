package com.ccapp.credit.service;

import com.ccapp.circles.repository.CircleMemberRepository;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.circles.domain.enums.CircleStatus;
import com.ccapp.admin.domain.enums.AdminPolicyType;
import com.ccapp.admin.repository.AdminPolicyRepository;
import com.ccapp.circlesocial.domain.enums.ActivityType;
import com.ccapp.circlesocial.event.CircleActivityRequestedEvent;
import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.country.dto.CountryContext;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.service.CountryConfigResolver;
import com.ccapp.country.service.CountryContextService;
import com.ccapp.credit.domain.entity.Loan;
import com.ccapp.credit.domain.entity.RepaymentSchedule;
import com.ccapp.credit.domain.enums.InstallmentStatus;
import com.ccapp.credit.domain.enums.LoanStatus;
import com.ccapp.credit.dto.CreateLoanRequest;
import com.ccapp.credit.dto.CreditEligibilityResponse;
import com.ccapp.credit.dto.LoanResponse;
import com.ccapp.credit.dto.RepaymentScheduleResponse;
import com.ccapp.credit.repository.LoanRepository;
import com.ccapp.credit.repository.RepaymentScheduleRepository;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.financing.domain.entity.FinancialPartner;
import com.ccapp.financing.domain.enums.FinancialPartnerStatus;
import com.ccapp.financing.domain.enums.InstitutionLedgerEntryType;
import com.ccapp.financing.repository.FinancialPartnerRepository;
import com.ccapp.financing.service.InstitutionLedgerService;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.progress.event.LoanDisbursedEvent;
import com.ccapp.wallet.event.WalletCreditedEvent;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoanService {

    private static final long DEFAULT_MIN_FINANCIAL_MEMBERS_PER_CIRCLE = 3;
    private static final long DEFAULT_MAX_FINANCIAL_MEMBERS_PER_CIRCLE = 5;
    private static final EnumSet<LoanStatus> CIRCLE_LOCKING_STATUSES = EnumSet.of(
            LoanStatus.APPROVED,
            LoanStatus.DISBURSED,
            LoanStatus.ACTIVE
    );

    private final LoanRepository loanRepository;
    private final RepaymentScheduleRepository repaymentScheduleRepository;
    private final AccountHolderService accountHolderService;
    private final CircleRepository circleRepository;
    private final CircleMemberRepository circleMemberRepository;
    private final AccessControlService accessControlService;
    private final CreditScoringService creditScoringService;
    private final CountryContextService countryContextService;
    private final CountryConfigResolver countryConfigResolver;
    private final AdminPolicyRepository adminPolicyRepository;
    private final FinancialPartnerRepository financialPartnerRepository;
    private final InstitutionLedgerService institutionLedgerService;
    private final WalletRepository walletRepository;
    private final OperationService operationService;
    private final AccountingPostingService accountingPostingService;
    private final DomainEventPublisher domainEventPublisher;

    @Transactional
    public LoanResponse createLoan(UUID accountHolderId, UUID actorUserId, CreateLoanRequest request) {
        if (actorUserId == null) {
            throw new BusinessException("Actor user is required");
        }

        CountryContext countryContext = countryContextService.resolve(accountHolderId);
        String serviceCountryCode = countryContext.serviceCountryCode();
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.TRANSACT);
        ensureCreditPolicyConfigured(serviceCountryCode);

        var circle = circleRepository.findByIdAndStatus(request.circleId(), CircleStatus.ACTIVE)
                .orElse(null);
        if (circle == null) {
            throw new BusinessException("Circle not found");
        }
        if (!serviceCountryCode.equalsIgnoreCase(circle.getCountryCode())) {
            throw new BusinessException("Circle country must match account holder service country");
        }
        if (!circleMemberRepository.existsByCircle_IdAndAccountHolderIdAndFinancialMemberTrue(request.circleId(), accountHolderId)) {
            throw new BusinessException("Account holder is not a member of this circle");
        }
        CircleMemberBounds bounds = resolveCircleMemberBounds(serviceCountryCode);
        long financialMembersCount = circleMemberRepository.countByCircle_IdAndFinancialMemberTrue(request.circleId());
        if (financialMembersCount < bounds.minFinancialMembers() || financialMembersCount > bounds.maxFinancialMembers()) {
            throw new BusinessException("Circle members count is outside configured limits");
        }
        if (loanRepository.existsByCircleIdAndStatusIn(request.circleId(), CIRCLE_LOCKING_STATUSES)) {
            throw new BusinessException("CIRCLE_LOCKED_BY_ACTIVE_LOAN");
        }
        if (!countryContext.currency().equalsIgnoreCase(request.currency())) {
            throw new BusinessException("Loan currency must match account holder country currency");
        }

        CreditEligibilityResponse eligibility = creditScoringService.evaluateEligibility(accountHolderId, request.circleId());

        Loan loan = Loan.builder()
                .accountHolderId(accountHolderId)
                .circleId(request.circleId())
                .countryCode(serviceCountryCode)
                .amount(request.amount())
                .currency(request.currency().toUpperCase())
                .interestRate(request.interestRate())
                .durationMonths(request.durationMonths())
                .status(LoanStatus.PENDING)
                .build();

        if (request.amount().compareTo(eligibility.maxEligibleAmount()) > 0) {
            loan.setStatus(LoanStatus.REJECTED);
            Loan rejected = loanRepository.save(loan);
            return toResponse(rejected, List.of());
        }

        OffsetDateTime now = OffsetDateTime.now();
        loan.setStatus(LoanStatus.APPROVED);
        loan.setApprovedAt(now);

        Loan saved = loanRepository.save(loan);
        List<RepaymentSchedule> schedules = createRepaymentSchedule(saved);

        return toResponse(saved, schedules);
    }

    @Transactional
    public Loan disburseApprovedLoan(UUID loanId, UUID actorUserId, UUID institutionId, String referenceId) {
        if (actorUserId == null) {
            throw new BusinessException("Actor user is required");
        }
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new NotFoundException("Loan not found"));

        accessControlService.assertCanActOnAccountHolder(actorUserId, loan.getAccountHolderId(), PermissionLevel.TRANSACT);
        if (loan.getStatus() == LoanStatus.DISBURSED
                && referenceId != null
                && referenceId.equalsIgnoreCase(loan.getDisbursementReferenceId())) {
            operationService.findByIdempotencyKey("loan-disbursement:" + loan.getId())
                    .ifPresent(existing -> accountingPostingService.postConfirmedOperation(existing.getId()));
            if (loan.getInstitutionId() != null && loan.getDisbursementReferenceId() != null) {
                institutionLedgerService.recordEntry(
                        loan.getAccountHolderId(),
                        loan.getInstitutionId(),
                        InstitutionLedgerEntryType.LOAN,
                        loan.getAmount(),
                        loan.getCurrency(),
                        loan.getDisbursementReferenceId()
                );
            }
            return loan;
        }
        if (loan.getStatus() != LoanStatus.APPROVED) {
            throw new BusinessException("Only approved loans can be disbursed");
        }

        FinancialPartner institution = loadInstitution(institutionId, loan.getCountryCode(), loan.getCurrency());
        String normalizedReferenceId = normalizeReference(referenceId, "Loan disbursement reference is required");

        loan.setStatus(LoanStatus.DISBURSED);
        loan.setInstitutionId(institution.getId());
        loan.setDisbursementReferenceId(normalizedReferenceId);
        loan.setDisbursedAt(OffsetDateTime.now());
        Loan saved = loanRepository.save(loan);
        Operation disbursementOperation = operationService.createInitiatedOperation(
                saved.getAccountHolderId(),
                actorUserId,
                OperationType.LOAN_DISBURSEMENT,
                saved.getAmount(),
                saved.getCurrency(),
                "loan-disbursement:" + saved.getId(),
                "Loan disbursement for loan " + saved.getId()
        );
        Operation confirmedOperation = disbursementOperation.getStatus() == OperationStatus.CONFIRMED
                ? disbursementOperation
                : operationService.confirmOperation(disbursementOperation.getId());
        accountingPostingService.postConfirmedOperation(confirmedOperation.getId());
        Wallet beneficiaryWallet = requireActiveWallet(saved.getAccountHolderId());
        domainEventPublisher.publish(WalletCreditedEvent.create(
                beneficiaryWallet.getId(),
                saved.getAccountHolderId(),
                saved.getAmount(),
                saved.getCurrency(),
                confirmedOperation.getId()
        ));
        institutionLedgerService.recordEntry(
                saved.getAccountHolderId(),
                institution.getId(),
                InstitutionLedgerEntryType.LOAN,
                saved.getAmount(),
                saved.getCurrency(),
                normalizedReferenceId
        );

        domainEventPublisher.publish(CircleActivityRequestedEvent.create(
                saved.getCircleId(),
                actorUserId,
                ActivityType.LOAN_GRANTED,
                saved.getId(),
                "Loan disbursed"
        ));
        domainEventPublisher.publish(LoanDisbursedEvent.create(saved.getAccountHolderId(), saved.getId()));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<LoanResponse> getMyLoans(UUID accountHolderId) {
        accountHolderService.getById(accountHolderId);

        return loanRepository.findByAccountHolderIdOrderByCreatedAtDesc(accountHolderId).stream()
                .map(loan -> toResponse(loan, repaymentScheduleRepository.findByLoan_IdOrderByInstallmentNumberAsc(loan.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public LoanResponse toResponse(Loan loan) {
        return toResponse(loan, repaymentScheduleRepository.findByLoan_IdOrderByInstallmentNumberAsc(loan.getId()));
    }

    private void ensureCreditPolicyConfigured(String countryCode) {
        boolean hasVersionedGuaranteeConfig = countryConfigResolver.resolveActiveConfig(
                countryCode,
                CountryConfigType.GUARANTEE
        ).source() != CountryConfigResolver.ConfigSource.EMPTY;
        boolean hasLegacyPolicy = adminPolicyRepository.findFirstByPolicyTypeAndCountryCodeOrderByCreatedAtDesc(AdminPolicyType.CREDIT_POLICY, countryCode).isPresent()
                || adminPolicyRepository.findFirstByPolicyTypeAndCountryCodeIsNullOrderByCreatedAtDesc(AdminPolicyType.CREDIT_POLICY).isPresent();
        if (!hasVersionedGuaranteeConfig && !hasLegacyPolicy) {
            throw new BusinessException("Guarantee policy not configured for country");
        }
    }

    private CircleMemberBounds resolveCircleMemberBounds(String countryCode) {
        CountryConfigResolver.ResolvedConfig config = countryConfigResolver.resolveActiveConfig(
                countryCode,
                CountryConfigType.GENERAL
        );
        ObjectNode payload = countryConfigResolver.payloadAsObjectNode(config);
        ObjectNode circlePayload = payload.has("circle") && payload.get("circle").isObject()
                ? (ObjectNode) payload.get("circle")
                : payload;
        long min = longField(circlePayload, "minMembers", DEFAULT_MIN_FINANCIAL_MEMBERS_PER_CIRCLE);
        long max = longField(circlePayload, "maxMembers", DEFAULT_MAX_FINANCIAL_MEMBERS_PER_CIRCLE);
        if (max < min) {
            throw new BusinessException("general.circle.maxMembers must be >= minMembers");
        }
        return new CircleMemberBounds(min, max);
    }

    private long longField(ObjectNode payload, String fieldName, long defaultValue) {
        if (payload == null || !payload.has(fieldName) || payload.get(fieldName).isNull()) {
            return defaultValue;
        }
        if (!payload.get(fieldName).canConvertToLong()) {
            throw new BusinessException(fieldName + " must be an integer");
        }
        long value = payload.get(fieldName).asLong();
        return value > 0 ? value : defaultValue;
    }

    private List<RepaymentSchedule> createRepaymentSchedule(Loan loan) {
        BigDecimal totalDue = loan.getAmount().add(
                loan.getAmount().multiply(loan.getInterestRate()).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
        );

        BigDecimal installmentAmount = totalDue
                .divide(BigDecimal.valueOf(loan.getDurationMonths()), 4, RoundingMode.HALF_UP);

        LocalDate startDate = LocalDate.now();
        List<RepaymentSchedule> schedules = new ArrayList<>();
        BigDecimal accumulated = BigDecimal.ZERO;

        for (int i = 1; i <= loan.getDurationMonths(); i++) {
            BigDecimal amountDue = (i == loan.getDurationMonths())
                    ? totalDue.subtract(accumulated)
                    : installmentAmount;
            accumulated = accumulated.add(amountDue);

            schedules.add(RepaymentSchedule.builder()
                    .loan(loan)
                    .installmentNumber(i)
                    .amountDue(amountDue)
                    .dueDate(startDate.plusMonths(i))
                    .status(InstallmentStatus.PENDING)
                    .build());
        }

        return repaymentScheduleRepository.saveAll(schedules);
    }

    private FinancialPartner loadInstitution(UUID institutionId, String countryCode, String currency) {
        if (institutionId == null) {
            throw new BusinessException("Institution is required");
        }
        FinancialPartner institution = financialPartnerRepository.findById(institutionId)
                .orElseThrow(() -> new NotFoundException("Institution not found"));
        if (institution.getStatus() != FinancialPartnerStatus.ACTIVE) {
            throw new BusinessException("Institution is not active");
        }
        if (!countryCode.equalsIgnoreCase(institution.getCountryCode())) {
            throw new BusinessException("Institution country must match loan country");
        }
        if (!currency.equalsIgnoreCase(institution.getCurrency())) {
            throw new BusinessException("Institution currency must match loan currency");
        }
        return institution;
    }

    private String normalizeReference(String referenceId, String message) {
        if (referenceId == null || referenceId.isBlank()) {
            throw new BusinessException(message);
        }
        return referenceId.trim().toUpperCase(Locale.ROOT);
    }

    private Wallet requireActiveWallet(UUID accountHolderId) {
        return walletRepository.findFirstByAccountHolderIdAndStatusOrderByCreatedAtDesc(accountHolderId, "ACTIVE")
                .orElseThrow(() -> new BusinessException("Active wallet not found for account holder"));
    }

    private LoanResponse toResponse(Loan loan, List<RepaymentSchedule> schedules) {
        List<RepaymentScheduleResponse> scheduleResponses = schedules.stream()
                .map(s -> new RepaymentScheduleResponse(
                        s.getId(),
                        s.getInstallmentNumber(),
                        s.getAmountDue(),
                        s.getDueDate(),
                        s.getStatus()
                ))
                .toList();

        return new LoanResponse(
                loan.getId(),
                loan.getAccountHolderId(),
                loan.getCircleId(),
                loan.getInstitutionId(),
                loan.getAmount(),
                loan.getCurrency(),
                loan.getInterestRate(),
                loan.getDurationMonths(),
                loan.getStatus(),
                loan.getApprovedAt(),
                loan.getDisbursedAt(),
                loan.getDisbursementReferenceId(),
                loan.getCreatedAt(),
                scheduleResponses
        );
    }

    private record CircleMemberBounds(long minFinancialMembers, long maxFinancialMembers) {
    }
}
