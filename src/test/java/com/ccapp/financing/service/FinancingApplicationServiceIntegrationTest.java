package com.ccapp.financing.service;

import com.ccapp.common.api.PageResponse;
import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.config.TestJacksonAndCachingConfig;
import com.ccapp.country.config.service.CountryConfigResolver;
import com.ccapp.country.dto.CountryContext;
import com.ccapp.financing.domain.entity.FinancialPartner;
import com.ccapp.financing.domain.entity.FinancingOffer;
import com.ccapp.financing.domain.entity.LoanApplication;
import com.ccapp.financing.domain.entity.PartnerLedgerAccount;
import com.ccapp.financing.domain.entity.InstitutionLedgerEntry;
import com.ccapp.financing.domain.enums.FinancialPartnerStatus;
import com.ccapp.financing.domain.enums.FinancialPartnerType;
import com.ccapp.financing.domain.enums.FinancingOfferStatus;
import com.ccapp.financing.domain.enums.InstitutionLedgerEntryType;
import com.ccapp.financing.domain.enums.LoanApplicationStatus;
import com.ccapp.financing.domain.enums.PartnerLedgerEntryType;
import com.ccapp.financing.dto.CreateLoanApplicationRequest;
import com.ccapp.financing.dto.LoanApplicationResponse;
import com.ccapp.financing.repository.FinancialPartnerRepository;
import com.ccapp.financing.repository.FinancingOfferRepository;
import com.ccapp.financing.repository.InstitutionLedgerEntryRepository;
import com.ccapp.financing.repository.LoanApplicationRepository;
import com.ccapp.financing.repository.PartnerLedgerAccountRepository;
import com.ccapp.financing.repository.PartnerLedgerEntryRepository;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({FinancingApplicationService.class, PartnerLedgerService.class, PartnerReportingService.class, InstitutionLedgerService.class, TestJacksonAndCachingConfig.class})
class FinancingApplicationServiceIntegrationTest {

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
    @Autowired
    private InstitutionLedgerEntryRepository institutionLedgerEntryRepository;

    @MockBean
    private AccountHolderService accountHolderService;
    @MockBean
    private AccessControlService accessControlService;
    @MockBean
    private com.ccapp.country.service.CountryContextService countryContextService;
    @MockBean
    private PartnerBackofficeAccessService partnerBackofficeAccessService;
    @MockBean
    private CountryConfigResolver countryConfigResolver;
    @MockBean
    private DomainEventPublisher domainEventPublisher;

    private UUID accountHolderId;
    private FinancingOffer offer;

    @BeforeEach
    void setUp() {
        accountHolderId = UUID.randomUUID();
        when(accountHolderService.getById(any())).thenAnswer(invocation -> AccountHolder.builder()
                .id(invocation.getArgument(0))
                .firstName("Applicant")
                .lastName("Member")
                .build());
        doNothing().when(accessControlService).assertCanActOnAccountHolder(any(), any(), any());
        when(accessControlService.canActOnAccountHolder(any(), any(), any())).thenReturn(true);
        when(countryContextService.resolve(any())).thenReturn(new CountryContext("BJ", "BJ", "XOF"));
        when(partnerBackofficeAccessService.canAccessPartner(any(), any())).thenReturn(false);
        when(countryConfigResolver.resolveActiveConfig(any(), any())).thenAnswer(invocation -> new CountryConfigResolver.ResolvedConfig(
                invocation.getArgument(0),
                invocation.getArgument(1),
                1,
                "{}",
                CountryConfigResolver.ConfigSource.EMPTY
        ));
        when(countryConfigResolver.payloadAsObjectNode(any(CountryConfigResolver.ResolvedConfig.class)))
                .thenReturn(JsonNodeFactory.instance.objectNode());

        FinancialPartner partner = financialPartnerRepository.save(FinancialPartner.builder()
                .name("Partner One")
                .type(FinancialPartnerType.BANK)
                .countryCode("BJ")
                .currency("XOF")
                .status(FinancialPartnerStatus.ACTIVE)
                .build());
        offer = financingOfferRepository.save(FinancingOffer.builder()
                .partnerId(partner.getId())
                .name("SME Loan")
                .description("Working capital")
                .loanType("SME")
                .minAmount(new BigDecimal("10.00"))
                .maxAmount(new BigDecimal("1000.00"))
                .interestRate(new BigDecimal("5.00"))
                .durationMonths(6)
                .currency("XOF")
                .status(FinancingOfferStatus.ACTIVE)
                .build());
    }

    @Test
    void createListAndGetApplication_shouldPersistLedgerSubmissionEntry() {
        LoanApplicationResponse created = financingApplicationService.createApplication(
                UUID.randomUUID(),
                accountHolderId,
                new CreateLoanApplicationRequest(
                        offer.getId(),
                        null,
                        new BigDecimal("100.00")
                )
        );

        assertNotNull(created.id());
        assertEquals(LoanApplicationStatus.SUBMITTED, created.status());
        assertEquals(1L, partnerLedgerEntryRepository.count());
        assertEquals(PartnerLedgerEntryType.APPLICATION_SUBMITTED,
                partnerLedgerEntryRepository.findByApplicationIdOrderByCreatedAtAsc(created.id()).get(0).getEntryType());

        PartnerLedgerAccount ledgerAccount = partnerLedgerAccountRepository
                .findByPartnerIdAndCurrency(offer.getPartnerId(), "XOF")
                .orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(ledgerAccount.getBalance()));

        PageResponse<LoanApplicationResponse> page = financingApplicationService.listApplications(
                UUID.randomUUID(),
                accountHolderId,
                null,
                null,
                0,
                20
        );
        assertEquals(1, page.items().size());
        assertEquals(created.id(), page.items().get(0).id());

        LoanApplicationResponse loaded = financingApplicationService.getApplication(
                UUID.randomUUID(),
                accountHolderId,
                created.id()
        );
        assertEquals(created.id(), loaded.id());

        LoanApplication persisted = loanApplicationRepository.findById(created.id()).orElseThrow();
        assertNotNull(persisted.getConfigSnapshot());
        assertEquals(1, persisted.getConfigVersion());
        assertEquals(0, institutionLedgerEntryRepository.count());
    }
}
