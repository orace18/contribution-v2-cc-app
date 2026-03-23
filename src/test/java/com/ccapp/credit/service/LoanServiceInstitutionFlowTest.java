package com.ccapp.credit.service;

import com.ccapp.common.events.DomainEvent;
import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.circlesocial.event.CircleActivityRequestedEvent;
import com.ccapp.credit.domain.entity.Loan;
import com.ccapp.credit.domain.enums.LoanStatus;
import com.ccapp.credit.repository.LoanRepository;
import com.ccapp.credit.repository.RepaymentScheduleRepository;
import com.ccapp.financing.domain.entity.FinancialPartner;
import com.ccapp.financing.domain.enums.FinancialPartnerStatus;
import com.ccapp.financing.domain.enums.FinancialPartnerType;
import com.ccapp.financing.domain.enums.InstitutionLedgerEntryType;
import com.ccapp.financing.service.InstitutionLedgerService;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.progress.event.LoanDisbursedEvent;
import com.ccapp.financing.repository.FinancialPartnerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanServiceInstitutionFlowTest {

    @Mock
    private LoanRepository loanRepository;
    @Mock
    private RepaymentScheduleRepository repaymentScheduleRepository;
    @Mock
    private AccountHolderService accountHolderService;
    @Mock
    private com.ccapp.circles.repository.CircleRepository circleRepository;
    @Mock
    private com.ccapp.circles.repository.CircleMemberRepository circleMemberRepository;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private CreditScoringService creditScoringService;
    @Mock
    private com.ccapp.country.service.CountryContextService countryContextService;
    @Mock
    private com.ccapp.country.config.service.CountryConfigResolver countryConfigResolver;
    @Mock
    private com.ccapp.admin.repository.AdminPolicyRepository adminPolicyRepository;
    @Mock
    private FinancialPartnerRepository financialPartnerRepository;
    @Mock
    private InstitutionLedgerService institutionLedgerService;
    @Mock
    private DomainEventPublisher domainEventPublisher;

    @InjectMocks
    private LoanService loanService;

    @Test
    void disburseApprovedLoan_shouldRecordInstitutionLedgerWithoutInternalAccounting() {
        UUID actorUserId = UUID.randomUUID();
        UUID accountHolderId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        UUID loanId = UUID.randomUUID();

        Loan approvedLoan = Loan.builder()
                .id(loanId)
                .accountHolderId(accountHolderId)
                .circleId(UUID.randomUUID())
                .countryCode("BJ")
                .amount(new BigDecimal("10000"))
                .currency("XOF")
                .interestRate(new BigDecimal("5"))
                .durationMonths(6)
                .status(LoanStatus.APPROVED)
                .build();

        FinancialPartner institution = FinancialPartner.builder()
                .id(institutionId)
                .countryCode("BJ")
                .currency("XOF")
                .type(FinancialPartnerType.IMF)
                .status(FinancialPartnerStatus.ACTIVE)
                .build();

        when(loanRepository.findById(loanId)).thenReturn(Optional.of(approvedLoan));
        doNothing().when(accessControlService).assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.TRANSACT);
        when(financialPartnerRepository.findById(institutionId)).thenReturn(Optional.of(institution));
        when(loanRepository.save(any(Loan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Loan saved = loanService.disburseApprovedLoan(loanId, actorUserId, institutionId, "INST-DISB-1");

        assertEquals(LoanStatus.DISBURSED, saved.getStatus());
        assertEquals(institutionId, saved.getInstitutionId());
        assertEquals("INST-DISB-1", saved.getDisbursementReferenceId());
        verify(institutionLedgerService).recordEntry(
                accountHolderId,
                institutionId,
                InstitutionLedgerEntryType.LOAN,
                new BigDecimal("10000"),
                "XOF",
                "INST-DISB-1"
        );

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(domainEventPublisher, times(2)).publish(eventCaptor.capture());
        List<DomainEvent> events = eventCaptor.getAllValues();
        long disbursedEvents = events.stream().filter(LoanDisbursedEvent.class::isInstance).count();
        long activityEvents = events.stream().filter(CircleActivityRequestedEvent.class::isInstance).count();
        assertEquals(1L, disbursedEvents);
        assertEquals(1L, activityEvents);
    }
}
