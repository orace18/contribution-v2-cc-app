package com.ccapp.credit.service;

import com.ccapp.common.events.DomainEvent;
import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.domain.entity.CountryChartAccount;
import com.ccapp.country.config.domain.enums.CountryAccountGroup;
import com.ccapp.country.config.domain.enums.CountryAccountSystemTag;
import com.ccapp.country.config.domain.enums.CountryChartAccountStatus;
import com.ccapp.country.config.domain.enums.CountryChartAccountType;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
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
import com.ccapp.financing.domain.enums.InstitutionLedgerEntryType;
import com.ccapp.financing.service.InstitutionLedgerService;
import com.ccapp.identity.delegation.domain.enums.DelegatedActionType;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.identity.delegation.service.DelegatedActionService;
import com.ccapp.wallet.service.WalletDebitFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    private InstitutionLedgerService institutionLedgerService;
    @Mock
    private DelegatedActionService delegatedActionService;
    @Mock
    private DomainEventPublisher domainEventPublisher;
    @Mock
    private WalletDebitFacade walletDebitFacade;
    @Mock
    private CountryChartOfAccountsService countryChartOfAccountsService;

    @InjectMocks
    private RepaymentService repaymentService;

    @Test
    void repay_shouldRecordInstitutionLedger_whenFinalInstallmentIsPaid() {
        UUID actorUserId = UUID.randomUUID();
        UUID loanId = UUID.randomUUID();
        UUID accountHolderId = UUID.randomUUID();
        UUID circleId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();

        Loan loan = Loan.builder()
                .id(loanId)
                .accountHolderId(accountHolderId)
                .circleId(circleId)
                .institutionId(institutionId)
                .currency("XOF")
                .countryCode("BJ")
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
                .institutionId(institutionId)
                .transactionId("TX-1")
                .paidAt(OffsetDateTime.now())
                .build();

        when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));
        doNothing().when(accessControlService).assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.TRANSACT);
        when(loanRepaymentRepository.findByTransactionId("TX-1")).thenReturn(Optional.empty());
        when(repaymentScheduleRepository.findByLoan_IdOrderByInstallmentNumberAsc(loanId)).thenReturn(List.of(nextInstallment));
        when(repaymentScheduleRepository.findFirstByLoan_IdAndStatusInOrderByInstallmentNumberAsc(eq(loanId), any()))
                .thenReturn(Optional.of(nextInstallment));
        when(loanRepaymentRepository.save(any(LoanRepayment.class))).thenReturn(persistedRepayment);
        when(repaymentScheduleRepository.save(any(RepaymentSchedule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repaymentScheduleRepository.countByLoan_IdAndStatusIn(eq(loanId), any())).thenReturn(0L);
        when(loanRepository.save(any(Loan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(countryChartOfAccountsService.resolveSystemAccount("BJ", CountryAccountSystemTag.LOAN_PORTFOLIO))
                .thenReturn(CountryChartAccount.builder()
                        .countryCode("BJ")
                        .accountCode("720001")
                        .accountNumber("720001")
                        .accountName("Loan Portfolio")
                        .accountGroup(CountryAccountGroup.LOAN_PORTFOLIO)
                        .accountType(CountryChartAccountType.ASSET)
                        .currency("XOF")
                        .openingBalance(BigDecimal.ZERO)
                        .status(CountryChartAccountStatus.ACTIVE)
                        .build());
        when(walletDebitFacade.executeSecureDebit(any()))
                .thenReturn(new WalletDebitFacade.WalletDebitExecutionResult(
                        true,
                        false,
                        UUID.randomUUID(),
                        "CONFIRMED",
                        null,
                        null
                ));

        LoanRepaymentResponse response = repaymentService.repay(
                actorUserId,
                loanId,
                new RepayLoanRequest(new BigDecimal("10000.00"), "XOF", "WALLET", institutionId, "TX-1", null, null, null)
        );

        assertEquals(loanId, response.loanId());
        assertEquals(LoanStatus.REPAID, response.loanStatus());
        assertEquals(0L, response.remainingInstallments());
        assertEquals(institutionId, response.institutionId());

        verify(institutionLedgerService).recordEntry(
                accountHolderId,
                institutionId,
                InstitutionLedgerEntryType.REPAYMENT,
                new BigDecimal("10000.00"),
                "XOF",
                "LOAN-REPAYMENT:TX-1"
        );

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(domainEventPublisher, org.mockito.Mockito.times(2)).publish(eventCaptor.capture());
        LoanRepaidEvent event = eventCaptor.getAllValues().stream()
                .filter(LoanRepaidEvent.class::isInstance)
                .map(LoanRepaidEvent.class::cast)
                .findFirst()
                .orElseThrow();
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
                .countryCode("BJ")
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
                        new RepayLoanRequest(new BigDecimal("9500.00"), "XOF", "WALLET", UUID.randomUUID(), "TX-2", null, null, null)
                )
        );

        verify(loanRepaymentRepository, never()).save(any());
        verify(institutionLedgerService, never()).recordEntry(any(), any(), any(), any(), any(), anyString());
        verify(domainEventPublisher, never()).publish(any());
    }
}
