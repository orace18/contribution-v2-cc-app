package com.ccapp.payments.subscription.service;

import com.ccapp.admin.service.ConfigAuditMetadataService;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.config.TestJacksonAndCachingConfig;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.dto.UpsertCountryConfigRequest;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.country.config.service.CountryConfigReadinessService;
import com.ccapp.country.config.service.CountryConfigResolver;
import com.ccapp.country.config.service.CountryConfigurationEngineService;
import com.ccapp.country.dto.CountryContext;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.repository.CountryRepository;
import com.ccapp.country.service.CountryContextService;
import com.ccapp.credit.dto.CreditEligibilityResponse;
import com.ccapp.credit.service.CreditScoringService;
import com.ccapp.financial.access.service.MemberFinancialProfileService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.mobile.home.dto.MobileHomeDashboardResponse;
import com.ccapp.mobile.home.service.HomeFacadeService;
import com.ccapp.mobile.onboarding.domain.entity.MobileOnboardingJourney;
import com.ccapp.mobile.onboarding.domain.enums.MobileOnboardingJourneyStatus;
import com.ccapp.mobile.onboarding.repository.MobileOnboardingJourneyRepository;
import com.ccapp.mobile.network.dto.MobileNetworkEligibilityResponse;
import com.ccapp.mobile.network.service.MobileNetworkService;
import com.ccapp.payments.commission.service.MlmCommissionService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.repository.OperationRepository;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.payments.subscription.domain.entity.Membership;
import com.ccapp.payments.subscription.domain.entity.SubscriptionPlan;
import com.ccapp.payments.subscription.domain.enums.MembershipStatus;
import com.ccapp.payments.subscription.domain.enums.SubscriptionGrade;
import com.ccapp.payments.subscription.repository.MembershipRepository;
import com.ccapp.payments.subscription.repository.SubscriptionPlanRepository;
import com.ccapp.platform.config.service.GlobalMlmConfigService;
import com.ccapp.platform.config.service.PlatformConfigurationService;
import com.ccapp.progress.service.TaskEngineService;
import com.ccapp.prayer.service.PrayerSuggestionService;
import com.ccapp.circles.service.CircleService;
import com.ccapp.circlesocial.service.CircleActivityService;
import com.ccapp.ledger.domain.entity.AccountingEvent;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.wallet.service.WalletLedgerProjectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.transaction.TestTransaction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        MembershipService.class,
        MembershipReconciliationService.class,
        SubscriptionPaymentFinalizationService.class,
        OperationService.class,
        MemberFinancialProfileService.class,
        MobileNetworkService.class,
        HomeFacadeService.class,
        CountryChartOfAccountsService.class,
        CountryConfigResolver.class,
        CountryConfigReadinessService.class,
        CountryConfigurationEngineService.class,
        PlatformConfigurationService.class,
        ConfigAuditMetadataService.class,
        TestJacksonAndCachingConfig.class
})
class ConfirmedMembershipVisibilityIntegrationTest {

    @Autowired
    private SubscriptionPaymentFinalizationService subscriptionPaymentFinalizationService;
    @Autowired
    private MembershipService membershipService;
    @Autowired
    private MemberFinancialProfileService memberFinancialProfileService;
    @Autowired
    private MobileNetworkService mobileNetworkService;
    @Autowired
    private HomeFacadeService homeFacadeService;
    @Autowired
    private MembershipRepository membershipRepository;
    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;
    @Autowired
    private OperationRepository operationRepository;
    @Autowired
    private CountryRepository countryRepository;
    @Autowired
    private AccountHolderRepository accountHolderRepository;
    @Autowired
    private CountryConfigurationEngineService countryConfigurationEngineService;
    @Autowired
    private CountryChartOfAccountsService countryChartOfAccountsService;
    @Autowired
    private MobileOnboardingJourneyRepository mobileOnboardingJourneyRepository;
    @Autowired
    private EntityManager entityManager;

    @MockBean
    private AccountingPostingService accountingPostingService;
    @MockBean
    private MlmCommissionService mlmCommissionService;
    @MockBean
    private com.ccapp.mobile.onboarding.service.MobileOnboardingJourneyService mobileOnboardingJourneyService;
    @MockBean
    private AccessControlService accessControlService;
    @MockBean
    private AccountHolderService accountHolderService;
    @MockBean
    private TaskEngineService taskEngineService;
    @MockBean
    private CircleService circleService;
    @MockBean
    private CircleActivityService circleActivityService;
    @MockBean
    private CreditScoringService creditScoringService;
    @MockBean
    private PrayerSuggestionService prayerSuggestionService;
    @MockBean
    private WalletLedgerProjectionService walletLedgerProjectionService;
    @MockBean
    private CountryContextService countryContextService;
    @MockBean
    private SubscriptionPlanCatalogService subscriptionPlanCatalogService;
    @MockBean
    private SubscriptionService subscriptionService;
    @MockBean
    private GlobalMlmConfigService globalMlmConfigService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        countryRepository.findByCode("BJ").orElseGet(() -> countryRepository.save(Country.builder()
                .id(UUID.randomUUID())
                .code("BJ")
                .name("Benin")
                .currency("XOF")
                .active(true)
                .configured(true)
                .fallback(false)
                .build()));
        countryChartOfAccountsService.autoGenerateRequiredAccounts("BJ");

        ObjectNode membershipPayload = objectMapper.createObjectNode();
        ArrayNode plans = objectMapper.createArrayNode();
        ObjectNode plan = objectMapper.createObjectNode();
        plan.put("code", "CLASSIC_12M");
        plan.put("name", "Classic 12 months");
        ObjectNode pricing = objectMapper.createObjectNode();
        pricing.put("amount", 19000);
        pricing.put("currency", "XOF");
        plan.set("pricing", pricing);
        plan.set("benefits", objectMapper.createArrayNode().add("Community"));
        plans.add(plan);
        membershipPayload.set("plans", plans);
        countryConfigurationEngineService.upsertCountryConfiguration(
                "BJ",
                CountryConfigType.MEMBERSHIP,
                new UpsertCountryConfigRequest(membershipPayload, OffsetDateTime.now()),
                UUID.randomUUID()
        );

        doNothing().when(accessControlService).assertCanActOnAccountHolder(any(), any(), any());
        when(accountHolderService.getById(any())).thenAnswer(invocation -> {
            UUID accountHolderId = invocation.getArgument(0);
            return accountHolderRepository.findById(accountHolderId).orElseThrow();
        });
        when(countryContextService.resolve(any())).thenReturn(new CountryContext("BJ", "BJ", "XOF"));
        when(accountingPostingService.postConfirmedOperation(any())).thenReturn(AccountingEvent.builder()
                .id(UUID.randomUUID())
                .build());
        when(mlmCommissionService.calculateForOperation(any())).thenReturn(Optional.empty());
        when(taskEngineService.getAllUserTasks(any())).thenReturn(List.of());
        doThrow(new NotFoundException("No active circle found for user")).when(circleService).getMyCircle(any());
        when(creditScoringService.getEligibility(any(), any())).thenReturn(new CreditEligibilityResponse(
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                OffsetDateTime.now()
        ));
        when(prayerSuggestionService.getHomeSuggestion(any(), anyBoolean(), anyBoolean(), anyBoolean())).thenReturn(null);
    }

    @Test
    void finalizeConfirmedOperation_shouldMakeMembershipVisibleAndSponsorAbsenceExplicit() {
        UUID accountHolderId = UUID.randomUUID();
        accountHolderRepository.saveAndFlush(AccountHolder.builder()
                .id(accountHolderId)
                .firstName("Elie")
                .lastName("Houe")
                .phoneNumber("+2290102030405")
                .countryCode("BJ")
                .serviceCountryCode("BJ")
                .phoneVerifiedAt(OffsetDateTime.now().minusDays(1))
                .build());

        SubscriptionPlan plan = getOrCreateClassicPlan();

        mobileOnboardingJourneyRepository.saveAndFlush(MobileOnboardingJourney.builder()
                .accountHolderId(accountHolderId)
                .status(MobileOnboardingJourneyStatus.SPONSOR_SET)
                .countryCode("BJ")
                .sponsorSet(true)
                .sponsorAccountHolderId(null)
                .networkLinked(false)
                .onboardingCompleted(false)
                .build());

        Operation operation = operationRepository.saveAndFlush(Operation.builder()
                .accountHolderId(accountHolderId)
                .actorUserId(accountHolderId)
                .type(OperationType.SUBSCRIPTION)
                .status(OperationStatus.CONFIRMED)
                .amount(new BigDecimal("19000"))
                .currency("XOF")
                .idempotencyKey("confirmed-membership:" + UUID.randomUUID())
                .description("Confirmed membership visibility test")
                .planId(plan.getId())
                .paymentMethod("CC_WALLET")
                .build());

        membershipService.createPendingMembership(operation.getId(), accountHolderId, plan);

        subscriptionPaymentFinalizationService.finalizeConfirmedOperation(operation.getId());

        Membership membership = membershipRepository.findByOperationId(operation.getId()).orElseThrow();
        assertEquals(MembershipStatus.ACTIVE, membership.getStatus());
        assertEquals(SubscriptionGrade.CLASSIC, membership.getPlanGrade());
        assertTrue(memberFinancialProfileService.getProfile(accountHolderId).membershipActive());

        MobileNetworkEligibilityResponse eligibility = mobileNetworkService.getEligibility(accountHolderId, accountHolderId);
        assertFalse(eligibility.hasSponsor());
        assertTrue(eligibility.phoneVerified());
        assertTrue(eligibility.hasPaidMembership());
        assertEquals("CLASSIC", eligibility.membershipGrade());
        assertFalse(eligibility.hasNetworkPosition());
        assertFalse(eligibility.isEligible());
        assertEquals("SPONSOR_DECLINED", eligibility.sponsorDecision());
        assertEquals("PENDING_PLACEMENT", eligibility.placementStatus());

        MobileHomeDashboardResponse home = homeFacadeService.getHome(accountHolderId, accountHolderId);
        assertEquals("CLASSIC", home.user().membershipType());
    }

    @Test
    void eligibilityRead_shouldAutoReconcilePendingMembershipWithConfirmedPayment() {
        UUID accountHolderId = UUID.randomUUID();
        accountHolderRepository.saveAndFlush(AccountHolder.builder()
                .id(accountHolderId)
                .firstName("Merveille")
                .lastName("Kiki")
                .phoneNumber("+2290199887766")
                .countryCode("BJ")
                .serviceCountryCode("BJ")
                .phoneVerifiedAt(OffsetDateTime.now().minusDays(1))
                .build());

        mobileOnboardingJourneyRepository.saveAndFlush(MobileOnboardingJourney.builder()
                .accountHolderId(accountHolderId)
                .status(MobileOnboardingJourneyStatus.PLAN_SELECTED)
                .countryCode("BJ")
                .sponsorSet(true)
                .sponsorAccountHolderId(null)
                .networkLinked(false)
                .onboardingCompleted(false)
                .build());

        SubscriptionPlan plan = getOrCreateClassicPlan();

        Operation operation = operationRepository.saveAndFlush(Operation.builder()
                .accountHolderId(accountHolderId)
                .actorUserId(accountHolderId)
                .type(OperationType.SUBSCRIPTION)
                .status(OperationStatus.CONFIRMED)
                .amount(new BigDecimal("19000"))
                .currency("XOF")
                .idempotencyKey("pending-confirmed-membership:" + UUID.randomUUID())
                .description("Pending membership reconciliation test")
                .planId(plan.getId())
                .paymentMethod("CC_WALLET")
                .build());

        Membership pendingMembership = membershipService.createPendingMembership(operation.getId(), accountHolderId, plan);
        assertEquals(MembershipStatus.PENDING, pendingMembership.getStatus());

        entityManager.flush();
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        MobileNetworkEligibilityResponse eligibility = mobileNetworkService.getEligibility(accountHolderId, accountHolderId);

        entityManager.clear();
        Membership reconciledMembership = membershipRepository.findByOperationId(operation.getId()).orElseThrow();
        assertEquals(MembershipStatus.ACTIVE, reconciledMembership.getStatus());
        assertTrue(memberFinancialProfileService.getProfile(accountHolderId).membershipActive());
        assertTrue(eligibility.hasPaidMembership());
        assertEquals("CLASSIC", eligibility.membershipGrade());
        assertEquals("SPONSOR_DECLINED", eligibility.sponsorDecision());
        assertEquals("PENDING_PLACEMENT", eligibility.placementStatus());

        MobileHomeDashboardResponse home = homeFacadeService.getHome(accountHolderId, accountHolderId);
        assertEquals("CLASSIC", home.user().membershipType());
    }

    private SubscriptionPlan getOrCreateClassicPlan() {
        return subscriptionPlanRepository.findByCodeAndCountryAndActiveTrue("CLASSIC_12M", "BJ")
                .orElseGet(() -> subscriptionPlanRepository.saveAndFlush(SubscriptionPlan.builder()
                        .code("CLASSIC_12M")
                        .name("Classic 12 months")
                        .grade(SubscriptionGrade.CLASSIC)
                        .durationMonths(12)
                        .country("BJ")
                        .currency("XOF")
                        .price(new BigDecimal("19000"))
                        .active(true)
                        .build()));
    }
}
