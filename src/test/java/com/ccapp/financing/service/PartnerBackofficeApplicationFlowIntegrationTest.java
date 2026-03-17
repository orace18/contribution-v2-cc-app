package com.ccapp.financing.service;

import com.ccapp.config.TestJacksonAndCachingConfig;
import com.ccapp.country.config.service.CountryConfigResolver;
import com.ccapp.country.dto.CountryContext;
import com.ccapp.financing.domain.entity.FinancialPartner;
import com.ccapp.financing.domain.entity.FinancingOffer;
import com.ccapp.financing.domain.entity.LoanApplication;
import com.ccapp.financing.domain.entity.PartnerLedgerAccount;
import com.ccapp.financing.domain.enums.FinancialPartnerStatus;
import com.ccapp.financing.domain.enums.FinancialPartnerType;
import com.ccapp.financing.domain.enums.FinancingOfferStatus;
import com.ccapp.financing.domain.enums.LoanApplicationStatus;
import com.ccapp.financing.domain.enums.PartnerLedgerEntryType;
import com.ccapp.financing.dto.LoanApplicationResponse;
import com.ccapp.financing.dto.ReviewLoanApplicationRequest;
import com.ccapp.financing.repository.FinancialPartnerRepository;
import com.ccapp.financing.repository.FinancingOfferRepository;
import com.ccapp.financing.repository.LoanApplicationRepository;
import com.ccapp.financing.repository.PartnerLedgerAccountRepository;
import com.ccapp.financing.repository.PartnerLedgerEntryRepository;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.service.OperationService;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({FinancingApplicationService.class, PartnerLedgerService.class, PartnerReportingService.class, TestJacksonAndCachingConfig.class})
class PartnerBackofficeApplicationFlowIntegrationTest {

    @Autowired
    private FinancingApplicationService financingApplicationService;
    @Autowired
    private FinancialPartnerRepository financialPartnerRepository;
    @Autowired
    private FinancingOfferRepository financingOfferRepository;
    @Autowired
    private LoanApplicationRepository loanApplicationRepository;
    @Autowired
    private PartnerLedgerEntryRepository partnerLedgerEntryRepository;
    @Autowired
    private PartnerLedgerAccountRepository partnerLedgerAccountRepository;

    @MockBean
    private AccountHolderService accountHolderService;
    @MockBean
    private AccessControlService accessControlService;
    @MockBean
    private com.ccapp.country.service.CountryContextService countryContextService;
    @MockBean
    private PartnerBackofficeAccessService partnerBackofficeAccessService;
    @MockBean
    private OperationService operationService;
    @MockBean
    private AccountingPostingService accountingPostingService;
    @MockBean
    private CountryConfigResolver countryConfigResolver;

    private FinancingOffer offer;
    private LoanApplication application;

    @BeforeEach
    void setUp() {
        when(accountHolderService.getById(any())).thenAnswer(invocation -> AccountHolder.builder()
                .id(invocation.getArgument(0))
                .firstName("Reviewer")
                .lastName("User")
                .build());
        doNothing().when(accessControlService).assertCanActOnAccountHolder(any(), any(), any());
        when(accessControlService.canActOnAccountHolder(any(), any(), any())).thenReturn(false);
        when(countryContextService.resolve(any())).thenReturn(new CountryContext("BJ", "BJ", "XOF"));
        doNothing().when(partnerBackofficeAccessService).assertHasAnyRole(any(), any(), any());
        when(partnerBackofficeAccessService.canAccessPartner(any(), any())).thenReturn(true);
        when(countryConfigResolver.resolveActiveConfig(any(), any())).thenAnswer(invocation -> new CountryConfigResolver.ResolvedConfig(
                invocation.getArgument(0),
                invocation.getArgument(1),
                null,
                "{}",
                CountryConfigResolver.ConfigSource.EMPTY
        ));
        when(countryConfigResolver.payloadAsObjectNode(any(CountryConfigResolver.ResolvedConfig.class)))
                .thenReturn(JsonNodeFactory.instance.objectNode());

        when(operationService.createInitiatedOperation(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> Operation.builder()
                        .id(UUID.randomUUID())
                        .accountHolderId(invocation.getArgument(0))
                        .actorUserId(invocation.getArgument(1))
                        .type(invocation.getArgument(2))
                        .status(OperationStatus.INITIATED)
                        .amount(invocation.getArgument(3))
                        .currency(invocation.getArgument(4))
                        .idempotencyKey(invocation.getArgument(5))
                        .description(invocation.getArgument(6))
                        .build());
        when(operationService.confirmOperation(any())).thenAnswer(invocation -> Operation.builder()
                .id(invocation.getArgument(0))
                .status(OperationStatus.CONFIRMED)
                .build());
        when(accountingPostingService.postConfirmedOperation(any())).thenReturn(null);

        FinancialPartner partner = financialPartnerRepository.save(FinancialPartner.builder()
                .name("Partner Review")
                .type(FinancialPartnerType.IMF)
                .countryCode("BJ")
                .status(FinancialPartnerStatus.ACTIVE)
                .build());
        offer = financingOfferRepository.save(FinancingOffer.builder()
                .partnerId(partner.getId())
                .name("Women Enterprise Loan")
                .description("Growth financing")
                .loanType("BUSINESS")
                .minAmount(new BigDecimal("50.00"))
                .maxAmount(new BigDecimal("5000.00"))
                .interestRate(new BigDecimal("6.50"))
                .durationMonths(12)
                .currency("XOF")
                .status(FinancingOfferStatus.ACTIVE)
                .build());
        application = loanApplicationRepository.save(LoanApplication.builder()
                .offerId(offer.getId())
                .accountHolderId(UUID.randomUUID())
                .requestedAmount(new BigDecimal("120.00"))
                .status(LoanApplicationStatus.SUBMITTED)
                .build());
    }

    @Test
    void reviewWorkflow_shouldGeneratePartnerLedgerEntriesAndCoreLedgerPostings() {
        UUID reviewerId = UUID.randomUUID();
        LoanApplicationResponse approved = financingApplicationService.reviewApplication(
                reviewerId,
                application.getId(),
                new ReviewLoanApplicationRequest(
                        LoanApplicationStatus.APPROVED,
                        new BigDecimal("100.00"),
                        "REF-100"
                )
        );
        assertEquals(LoanApplicationStatus.APPROVED, approved.status());
        assertEquals(0, new BigDecimal("100.00").compareTo(approved.approvedAmount()));

        LoanApplicationResponse disbursed = financingApplicationService.reviewApplication(
                reviewerId,
                application.getId(),
                new ReviewLoanApplicationRequest(
                        LoanApplicationStatus.DISBURSED,
                        null,
                        null
                )
        );
        assertEquals(LoanApplicationStatus.DISBURSED, disbursed.status());

        LoanApplicationResponse repaid = financingApplicationService.reviewApplication(
                reviewerId,
                application.getId(),
                new ReviewLoanApplicationRequest(
                        LoanApplicationStatus.REPAID,
                        null,
                        null
                )
        );
        assertEquals(LoanApplicationStatus.REPAID, repaid.status());

        List<PartnerLedgerEntryType> entryTypes = partnerLedgerEntryRepository
                .findByApplicationIdOrderByCreatedAtAsc(application.getId()).stream()
                .map(entry -> entry.getEntryType())
                .toList();
        assertEquals(List.of(
                PartnerLedgerEntryType.APPLICATION_APPROVED,
                PartnerLedgerEntryType.DISBURSEMENT,
                PartnerLedgerEntryType.REPAYMENT
        ), entryTypes);

        PartnerLedgerAccount ledgerAccount = partnerLedgerAccountRepository
                .findByPartnerIdAndCurrency(offer.getPartnerId(), "XOF")
                .orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(ledgerAccount.getBalance()));

        verify(operationService, times(2))
                .createInitiatedOperation(any(), any(), any(), any(), any(), any(), any());
        verify(operationService, times(2)).confirmOperation(any());
        verify(accountingPostingService, times(2)).postConfirmedOperation(any());
    }
}
