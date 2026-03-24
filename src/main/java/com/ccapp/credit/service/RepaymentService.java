package com.ccapp.credit.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.ConflictException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.country.config.domain.enums.CountryAccountSystemTag;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.credit.domain.entity.Loan;
import com.ccapp.credit.domain.entity.LoanRepayment;
import com.ccapp.credit.domain.entity.RepaymentSchedule;
import com.ccapp.credit.domain.enums.InstallmentStatus;
import com.ccapp.credit.domain.enums.LoanStatus;
import com.ccapp.credit.dto.LoanRepaymentResponse;
import com.ccapp.credit.dto.RepayLoanRequest;
import com.ccapp.credit.event.LoanRepaidEvent;
import com.ccapp.credit.repository.LoanRepaymentRepository;
import com.ccapp.credit.repository.LoanRepository;
import com.ccapp.credit.repository.RepaymentScheduleRepository;
import com.ccapp.financing.domain.enums.InstitutionLedgerEntryType;
import com.ccapp.financing.service.InstitutionLedgerService;
import com.ccapp.identity.delegation.domain.enums.DelegatedActionType;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.identity.delegation.service.DelegatedActionService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.wallet.service.WalletDebitFacade;
import com.ccapp.progress.event.RepaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RepaymentService {

    private static final String WALLET_PAYMENT_METHOD = "WALLET";

    private final LoanRepository loanRepository;
    private final LoanRepaymentRepository loanRepaymentRepository;
    private final RepaymentScheduleRepository repaymentScheduleRepository;
    private final AccessControlService accessControlService;
    private final InstitutionLedgerService institutionLedgerService;
    private final DelegatedActionService delegatedActionService;
    private final DomainEventPublisher domainEventPublisher;
    private final WalletDebitFacade walletDebitFacade;
    private final CountryChartOfAccountsService countryChartOfAccountsService;

    @Transactional
    public LoanRepaymentResponse repay(UUID actorUserId, UUID loanId, RepayLoanRequest request) {
        if (actorUserId == null) {
            throw new BusinessException("Actor user is required");
        }

        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new NotFoundException("Loan not found"));

        accessControlService.assertCanActOnAccountHolder(actorUserId, loan.getAccountHolderId(), PermissionLevel.TRANSACT);
        if (!(loan.getStatus() == LoanStatus.ACTIVE || loan.getStatus() == LoanStatus.DISBURSED)) {
            throw new BusinessException("Loan is not repayable");
        }
        if (!loan.getCurrency().equalsIgnoreCase(request.currency())) {
            throw new BusinessException("Repayment currency must match loan currency");
        }
        if (!WALLET_PAYMENT_METHOD.equalsIgnoreCase(request.paymentMethod())) {
            throw new BusinessException("Loan repayments must use WALLET");
        }

        markLateInstallments(loanId);

        RepaymentSchedule installment = repaymentScheduleRepository
                .findFirstByLoan_IdAndStatusInOrderByInstallmentNumberAsc(
                        loanId,
                        List.of(InstallmentStatus.PENDING, InstallmentStatus.LATE)
                )
                .orElseThrow(() -> new BusinessException("Loan already fully repaid"));

        if (request.amount().compareTo(installment.getAmountDue()) != 0) {
            throw new BusinessException("Repayment amount must match next installment amount");
        }

        String transactionId = resolveTransactionId(request.transactionId(), loanId, installment.getInstallmentNumber());
        LoanRepayment existingRepayment = loanRepaymentRepository.findByTransactionId(transactionId).orElse(null);
        if (existingRepayment != null) {
            if (!existingRepayment.getLoan().getId().equals(loanId)) {
                throw new ConflictException("Repayment transactionId already used");
            }
            long remaining = repaymentScheduleRepository.countByLoan_IdAndStatusIn(
                    loanId,
                    List.of(InstallmentStatus.PENDING, InstallmentStatus.LATE)
            );
            return toResponse(existingRepayment, installment.getInstallmentNumber(), loan.getStatus(), remaining, null);
        }

        String destinationAccountCode = countryChartOfAccountsService
                .resolveSystemAccount(loan.getCountryCode(), CountryAccountSystemTag.LOAN_PORTFOLIO)
                .getAccountCode();

        WalletDebitFacade.WalletDebitExecutionResult result = walletDebitFacade.executeSecureDebit(
                new WalletDebitFacade.SecureDebitRequest(
                        loan.getAccountHolderId(),
                        loan.getAccountHolderId(),
                        actorUserId,
                        null,
                        OperationType.LOAN_REPAYMENT,
                        request.amount(),
                        request.currency(),
                        transactionId,
                        "Loan repayment for loan " + loan.getId() + " installment " + installment.getInstallmentNumber(),
                        buildFingerprint(loan, installment, request, destinationAccountCode),
                        destinationAccountCode,
                        null,
                        null,
                        null,
                        request.pin(),
                        request.otpChallengeId(),
                        request.otpCode(),
                        false,
                        false,
                        "Loan repayment wallet debit",
                        "Loan portfolio settlement",
                        null,
                        null,
                        WalletDebitFacade.SecureDebitPostingMode.OPERATION_POSTING,
                        loan.getCountryCode()
                )
        );

        long remainingInstallments = repaymentScheduleRepository.countByLoan_IdAndStatusIn(
                loanId,
                List.of(InstallmentStatus.PENDING, InstallmentStatus.LATE)
        );
        if (result.otpRequired()) {
            return new LoanRepaymentResponse(
                    null,
                    loan.getId(),
                    installment.getInstallmentNumber(),
                    request.amount(),
                    request.currency().toUpperCase(Locale.ROOT),
                    request.paymentMethod(),
                    resolveInstitutionId(loan, request),
                    transactionId,
                    null,
                    loan.getStatus(),
                    remainingInstallments,
                    result.operationId(),
                    true,
                    result.otpChallengeId(),
                    result.otpExpiresAt()
            );
        }

        LoanRepayment repayment = loanRepaymentRepository.save(LoanRepayment.builder()
                .loan(loan)
                .amount(request.amount())
                .currency(request.currency().toUpperCase(Locale.ROOT))
                .paymentMethod(WALLET_PAYMENT_METHOD)
                .institutionId(resolveInstitutionId(loan, request))
                .transactionId(transactionId)
                .paidAt(OffsetDateTime.now())
                .build());

        installment.setStatus(InstallmentStatus.PAID);
        repaymentScheduleRepository.save(installment);

        institutionLedgerService.recordEntry(
                loan.getAccountHolderId(),
                repayment.getInstitutionId(),
                InstitutionLedgerEntryType.REPAYMENT,
                repayment.getAmount(),
                repayment.getCurrency(),
                repaymentReference(loan, repayment)
        );
        domainEventPublisher.publish(RepaymentCompletedEvent.create(
                loan.getAccountHolderId(),
                loan.getId(),
                repayment.getId()
        ));
        delegatedActionService.logIfDelegatedActor(
                loan.getAccountHolderId(),
                actorUserId,
                DelegatedActionType.REPAYMENT_RECORDED,
                "{\"loanId\":\"" + loan.getId() + "\",\"repaymentId\":\"" + repayment.getId() + "\"}"
        );

        remainingInstallments = repaymentScheduleRepository.countByLoan_IdAndStatusIn(
                loanId,
                List.of(InstallmentStatus.PENDING, InstallmentStatus.LATE)
        );

        if (remainingInstallments == 0) {
            loan.setStatus(LoanStatus.REPAID);
            loanRepository.save(loan);
            domainEventPublisher.publish(LoanRepaidEvent.create(
                    loan.getId(),
                    loan.getAccountHolderId(),
                    actorUserId,
                    loan.getCircleId(),
                    repayment.getAmount(),
                    repayment.getCurrency()
            ));
        }

        return toResponse(repayment, installment.getInstallmentNumber(), loan.getStatus(), remainingInstallments, result.operationId());
    }

    private void markLateInstallments(UUID loanId) {
        LocalDate today = LocalDate.now();
        List<RepaymentSchedule> schedules = repaymentScheduleRepository.findByLoan_IdOrderByInstallmentNumberAsc(loanId);

        boolean hasLateUpdate = false;
        for (RepaymentSchedule schedule : schedules) {
            if (schedule.getStatus() == InstallmentStatus.PENDING && schedule.getDueDate().isBefore(today)) {
                schedule.setStatus(InstallmentStatus.LATE);
                hasLateUpdate = true;
            }
        }

        if (hasLateUpdate) {
            repaymentScheduleRepository.saveAll(schedules);
        }
    }

    private UUID resolveInstitutionId(Loan loan, RepayLoanRequest request) {
        if (loan.getInstitutionId() == null && request.institutionId() == null) {
            throw new BusinessException("Institution is required for repayment recording");
        }
        if (loan.getInstitutionId() != null && request.institutionId() != null
                && !loan.getInstitutionId().equals(request.institutionId())) {
            throw new BusinessException("Repayment institution must match loan institution");
        }
        return request.institutionId() != null ? request.institutionId() : loan.getInstitutionId();
    }

    private String resolveTransactionId(String transactionId, UUID loanId, int installmentNumber) {
        if (transactionId == null || transactionId.isBlank()) {
            return "WALLET-LOAN-REPAYMENT:" + loanId + ":" + installmentNumber;
        }
        return transactionId.trim().toUpperCase(Locale.ROOT);
    }

    private String repaymentReference(Loan loan, LoanRepayment repayment) {
        if (repayment.getTransactionId() != null && !repayment.getTransactionId().isBlank()) {
            return "LOAN-REPAYMENT:" + repayment.getTransactionId();
        }
        return "LOAN-REPAYMENT:" + loan.getId() + ":" + repayment.getId();
    }

    private String buildFingerprint(Loan loan,
                                    RepaymentSchedule installment,
                                    RepayLoanRequest request,
                                    String destinationAccountCode) {
        return "loan-repayment|"
                + loan.getId()
                + "|" + installment.getInstallmentNumber()
                + "|" + request.amount().stripTrailingZeros().toPlainString()
                + "|" + request.currency().trim().toUpperCase(Locale.ROOT)
                + "|" + destinationAccountCode;
    }

    private LoanRepaymentResponse toResponse(LoanRepayment repayment,
                                             Integer installmentNumber,
                                             LoanStatus loanStatus,
                                             long remainingInstallments,
                                             UUID operationId) {
        return new LoanRepaymentResponse(
                repayment.getId(),
                repayment.getLoan().getId(),
                installmentNumber,
                repayment.getAmount(),
                repayment.getCurrency(),
                repayment.getPaymentMethod(),
                repayment.getInstitutionId(),
                repayment.getTransactionId(),
                repayment.getPaidAt(),
                loanStatus,
                remainingInstallments,
                operationId,
                false,
                null,
                null
        );
    }
}
