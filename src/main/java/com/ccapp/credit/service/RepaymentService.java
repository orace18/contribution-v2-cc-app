package com.ccapp.credit.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.ConflictException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.credit.event.LoanRepaidEvent;
import com.ccapp.credit.domain.entity.Loan;
import com.ccapp.credit.domain.entity.LoanRepayment;
import com.ccapp.credit.domain.entity.RepaymentSchedule;
import com.ccapp.credit.domain.enums.InstallmentStatus;
import com.ccapp.credit.domain.enums.LoanStatus;
import com.ccapp.credit.dto.LoanRepaymentResponse;
import com.ccapp.credit.dto.RepayLoanRequest;
import com.ccapp.credit.repository.LoanRepaymentRepository;
import com.ccapp.credit.repository.LoanRepository;
import com.ccapp.credit.repository.RepaymentScheduleRepository;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.domain.enums.DelegatedActionType;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.identity.delegation.service.DelegatedActionService;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.progress.event.RepaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RepaymentService {

    private final LoanRepository loanRepository;
    private final LoanRepaymentRepository loanRepaymentRepository;
    private final RepaymentScheduleRepository repaymentScheduleRepository;
    private final AccessControlService accessControlService;
    private final OperationService operationService;
    private final AccountingPostingService accountingPostingService;
    private final DelegatedActionService delegatedActionService;
    private final DomainEventPublisher domainEventPublisher;

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

        if (request.transactionId() != null && !request.transactionId().isBlank()
                && loanRepaymentRepository.findByTransactionId(request.transactionId()).isPresent()) {
            throw new ConflictException("Repayment transactionId already used");
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

        LoanRepayment repayment = loanRepaymentRepository.save(LoanRepayment.builder()
                .loan(loan)
                .amount(request.amount())
                .currency(request.currency().toUpperCase())
                .paymentMethod(request.paymentMethod())
                .transactionId(request.transactionId())
                .paidAt(OffsetDateTime.now())
                .build());

        installment.setStatus(InstallmentStatus.PAID);
        repaymentScheduleRepository.save(installment);

        triggerRepaymentLedger(loan, actorUserId, repayment);
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

        long remainingInstallments = repaymentScheduleRepository.countByLoan_IdAndStatusIn(
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

        return new LoanRepaymentResponse(
                repayment.getId(),
                loan.getId(),
                installment.getInstallmentNumber(),
                repayment.getAmount(),
                repayment.getCurrency(),
                repayment.getPaymentMethod(),
                repayment.getTransactionId(),
                repayment.getPaidAt(),
                loan.getStatus(),
                remainingInstallments
        );
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

    private void triggerRepaymentLedger(Loan loan, UUID actorUserId, LoanRepayment repayment) {
        String idempotencyKey = (repayment.getTransactionId() == null || repayment.getTransactionId().isBlank())
                ? "loan-repayment:" + repayment.getId()
                : "loan-repayment:" + repayment.getTransactionId();

        Operation operation = operationService.createInitiatedOperation(
                loan.getAccountHolderId(),
                actorUserId,
                OperationType.LOAN_REPAYMENT,
                repayment.getAmount(),
                repayment.getCurrency(),
                idempotencyKey,
                "Loan repayment " + loan.getId()
        );

        operationService.confirmOperation(operation.getId());
        accountingPostingService.postConfirmedOperation(operation.getId());
    }

}
