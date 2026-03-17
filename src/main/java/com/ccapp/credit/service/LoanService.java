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
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoanService {

    private static final long DEFAULT_MIN_FINANCIAL_MEMBERS_PER_CIRCLE = 3;
    private static final long DEFAULT_MAX_FINANCIAL_MEMBERS_PER_CIRCLE = 5;

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
    public Loan disburseApprovedLoan(UUID loanId, UUID actorUserId, String idempotencyKey) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new NotFoundException("Loan not found"));

        if (loan.getStatus() != LoanStatus.APPROVED) {
            throw new BusinessException("Only approved loans can be disbursed");
        }

        loan.setStatus(LoanStatus.ACTIVE);
        loan.setDisbursedAt(OffsetDateTime.now());
        Loan saved = loanRepository.save(loan);

        triggerDisbursementLedger(saved, actorUserId, idempotencyKey);
        domainEventPublisher.publish(CircleActivityRequestedEvent.create(
                saved.getCircleId(),
                actorUserId,
                ActivityType.LOAN_GRANTED,
                saved.getId(),
                "Loan disbursed"
        ));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<LoanResponse> getMyLoans(UUID accountHolderId) {
        accountHolderService.getById(accountHolderId);

        return loanRepository.findByAccountHolderIdOrderByCreatedAtDesc(accountHolderId).stream()
                .map(loan -> toResponse(loan, repaymentScheduleRepository.findByLoan_IdOrderByInstallmentNumberAsc(loan.getId())))
                .toList();
    }

    private void ensureCreditPolicyConfigured(String countryCode) {
        boolean hasVersionedRiskConfig = countryConfigResolver.resolveActiveConfig(
                countryCode,
                CountryConfigType.RISK_CONFIG
        ).source() != CountryConfigResolver.ConfigSource.EMPTY;
        boolean hasLegacyPolicy = adminPolicyRepository.findFirstByPolicyTypeAndCountryCodeOrderByCreatedAtDesc(AdminPolicyType.CREDIT_POLICY, countryCode).isPresent()
                || adminPolicyRepository.findFirstByPolicyTypeAndCountryCodeIsNullOrderByCreatedAtDesc(AdminPolicyType.CREDIT_POLICY).isPresent();
        if (!hasVersionedRiskConfig && !hasLegacyPolicy) {
            throw new BusinessException("Credit policy not configured for country");
        }
    }

    private CircleMemberBounds resolveCircleMemberBounds(String countryCode) {
        CountryConfigResolver.ResolvedConfig config = countryConfigResolver.resolveActiveConfig(
                countryCode,
                CountryConfigType.CIRCLE_CONFIG
        );
        ObjectNode payload = countryConfigResolver.payloadAsObjectNode(config);
        long min = longField(payload, "minMembers", DEFAULT_MIN_FINANCIAL_MEMBERS_PER_CIRCLE);
        long max = longField(payload, "maxMembers", DEFAULT_MAX_FINANCIAL_MEMBERS_PER_CIRCLE);
        if (max < min) {
            throw new BusinessException("circle_config.maxMembers must be >= minMembers");
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

    private void triggerDisbursementLedger(Loan loan, UUID actorUserId, String idempotencyKey) {
        String idem = (idempotencyKey == null || idempotencyKey.isBlank())
                ? "loan-disbursement:" + loan.getId()
                : idempotencyKey;

        Operation operation = operationService.createInitiatedOperation(
                loan.getAccountHolderId(),
                actorUserId,
                OperationType.LOAN_DISBURSEMENT,
                loan.getAmount(),
                loan.getCurrency(),
                idem,
                "Loan disbursement " + loan.getId()
        );

        operationService.confirmOperation(operation.getId());
        accountingPostingService.postConfirmedOperation(operation.getId());
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
                loan.getAmount(),
                loan.getCurrency(),
                loan.getInterestRate(),
                loan.getDurationMonths(),
                loan.getStatus(),
                loan.getApprovedAt(),
                loan.getDisbursedAt(),
                loan.getCreatedAt(),
                scheduleResponses
        );
    }

    private record CircleMemberBounds(long minFinancialMembers, long maxFinancialMembers) {
    }
}
