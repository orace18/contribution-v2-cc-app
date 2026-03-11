package com.ccapp.credit.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.credit.domain.entity.Loan;
import com.ccapp.credit.domain.entity.LoanRepayment;
import com.ccapp.credit.domain.entity.RepaymentSchedule;
import com.ccapp.credit.domain.enums.InstallmentStatus;
import com.ccapp.credit.domain.enums.LoanStatus;
import com.ccapp.credit.event.LoanRepaidEvent;
import com.ccapp.credit.dto.LoanRepaymentResponse;
import com.ccapp.credit.dto.RepayLoanRequest;
import com.ccapp.credit.repository.LoanRepaymentRepository;
import com.ccapp.credit.repository.LoanRepository;
import com.ccapp.credit.repository.RepaymentScheduleRepository;
import com.ccapp.identity.delegation.domain.enums.DelegatedActionType;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.identity.delegation.service.DelegatedActionService;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepaymentWorkflowRegressionTest {

    @Mock
    private LoanRepository loanRepository;
    @Mock
    private LoanRepaymentRepository loanRepaymentRepository;
    @Mock
    private RepaymentScheduleRepository repaymentScheduleRepository;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private OperationService operationService;
    @Mock
    private AccountingPostingService accountingPostingService;
    @Mock
    private DelegatedActionService delegatedActionService;
    @Mock
    private DomainEventPublisher domainEventPublisher;

    @InjectMocks
    private RepaymentService repaymentService;

    @Test
    void repay_shouldConfirmOperationAndPostLedger_whenFinalInstallmentIsPaid() {
        UUID actorUserId = UUID.randomUUID();
        UUID loanId = UUID.randomUUID();
        UUID accountHolderId = UUID.randomUUID();
        UUID circleId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();

        Loan loan = Loan.builder()
                .id(loanId)
                .accountHolderId(accountHolderId)
                .circleId(circleId)
                .currency("XOF")
                .status(LoanStatus.ACTIVE)
                .build();
        RepaymentSchedule nextInstallment = RepaymentSchedule.builder()
                .id(UUID.randomUUID())
                .loan(loan)
                .installmentNumber(3)
                .amountDue(new BigDecimal("10000.00"))
                .dueDate(LocalDate.now().plusDays(5))
                .status(InstallmentStatus.PENDING)
                .build();
        LoanRepayment persistedRepayment = LoanRepayment.builder()
                .id(UUID.randomUUID())
                .loan(loan)
                .amount(new BigDecimal("10000.00"))
                .currency("XOF")
                .paymentMethod("MOBILE_MONEY")
                .transactionId("TX-1")
                .paidAt(OffsetDateTime.now())
                .build();
        Operation operation = Operation.builder().id(operationId).type(OperationType.LOAN_REPAYMENT).build();

        when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));
        doNothing().when(accessControlService).assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.TRANSACT);
        when(loanRepaymentRepository.findByTransactionId("TX-1")).thenReturn(Optional.empty());
        when(repaymentScheduleRepository.findByLoan_IdOrderByInstallmentNumberAsc(loanId)).thenReturn(List.of(nextInstallment));
        when(repaymentScheduleRepository.findFirstByLoan_IdAndStatusInOrderByInstallmentNumberAsc(eq(loanId), any()))
                .thenReturn(Optional.of(nextInstallment));
        when(loanRepaymentRepository.save(any(LoanRepayment.class))).thenReturn(persistedRepayment);
        when(repaymentScheduleRepository.save(any(RepaymentSchedule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(operationService.createInitiatedOperation(
                eq(accountHolderId),
                eq(actorUserId),
                eq(OperationType.LOAN_REPAYMENT),
                eq(new BigDecimal("10000.00")),
                eq("XOF"),
                anyString(),
                anyString()
        )).thenReturn(operation);
        when(operationService.confirmOperation(operationId)).thenReturn(operation);
        when(repaymentScheduleRepository.countByLoan_IdAndStatusIn(eq(loanId), any())).thenReturn(0L);
        when(loanRepository.save(any(Loan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LoanRepaymentResponse response = repaymentService.repay(
                actorUserId,
                loanId,
                new RepayLoanRequest(new BigDecimal("10000.00"), "XOF", "MOBILE_MONEY", "TX-1")
        );

        assertEquals(loanId, response.loanId());
        assertEquals(LoanStatus.REPAID, response.loanStatus());
        assertEquals(0L, response.remainingInstallments());

        InOrder inOrder = org.mockito.Mockito.inOrder(operationService, accountingPostingService);
        inOrder.verify(operationService).confirmOperation(operationId);
        inOrder.verify(accountingPostingService).postConfirmedOperation(operationId);

        ArgumentCaptor<LoanRepaidEvent> eventCaptor = ArgumentCaptor.forClass(LoanRepaidEvent.class);
        verify(domainEventPublisher).publish(eventCaptor.capture());
        LoanRepaidEvent event = eventCaptor.getValue();
        assertEquals(loanId, event.loanId());
        assertEquals(accountHolderId, event.accountHolderId());
        assertEquals(actorUserId, event.actorUserId());
        assertEquals(circleId, event.circleId());
        assertEquals(new BigDecimal("10000.00"), event.amount());
        assertEquals("XOF", event.currency());
        verify(delegatedActionService).logIfDelegatedActor(eq(accountHolderId), eq(actorUserId), eq(DelegatedActionType.REPAYMENT_RECORDED), anyString());
    }

    @Test
    void repay_shouldReject_whenRepaymentAmountDoesNotMatchNextInstallment() {
        UUID actorUserId = UUID.randomUUID();
        UUID loanId = UUID.randomUUID();
        UUID accountHolderId = UUID.randomUUID();

        Loan loan = Loan.builder()
                .id(loanId)
                .accountHolderId(accountHolderId)
                .circleId(UUID.randomUUID())
                .currency("XOF")
                .status(LoanStatus.DISBURSED)
                .build();
        RepaymentSchedule nextInstallment = RepaymentSchedule.builder()
                .id(UUID.randomUUID())
                .loan(loan)
                .installmentNumber(1)
                .amountDue(new BigDecimal("10000.00"))
                .dueDate(LocalDate.now().plusDays(3))
                .status(InstallmentStatus.PENDING)
                .build();

        when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));
        doNothing().when(accessControlService).assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.TRANSACT);
        when(loanRepaymentRepository.findByTransactionId("TX-2")).thenReturn(Optional.empty());
        when(repaymentScheduleRepository.findByLoan_IdOrderByInstallmentNumberAsc(loanId)).thenReturn(List.of(nextInstallment));
        when(repaymentScheduleRepository.findFirstByLoan_IdAndStatusInOrderByInstallmentNumberAsc(eq(loanId), any()))
                .thenReturn(Optional.of(nextInstallment));

        assertThrows(
                BusinessException.class,
                () -> repaymentService.repay(
                        actorUserId,
                        loanId,
                        new RepayLoanRequest(new BigDecimal("9500.00"), "XOF", "MOBILE_MONEY", "TX-2")
                )
        );

        verify(loanRepaymentRepository, never()).save(any());
        verify(operationService, never()).createInitiatedOperation(any(), any(), any(), any(), any(), any(), any());
        verify(accountingPostingService, never()).postConfirmedOperation(any());
        verify(domainEventPublisher, never()).publish(any());
    }
}
