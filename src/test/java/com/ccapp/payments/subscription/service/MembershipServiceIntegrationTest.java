package com.ccapp.payments.subscription.service;

import com.ccapp.admin.service.ConfigAuditMetadataService;
import com.ccapp.config.TestJacksonAndCachingConfig;
import com.ccapp.country.config.dto.UpsertCountryConfigRequest;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.country.config.service.CountryConfigResolver;
import com.ccapp.country.config.service.CountryConfigReadinessService;
import com.ccapp.country.config.service.CountryConfigurationEngineService;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.repository.CountryRepository;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.repository.OperationRepository;
import com.ccapp.payments.subscription.domain.entity.Membership;
import com.ccapp.payments.subscription.domain.entity.SubscriptionPlan;
import com.ccapp.payments.subscription.domain.enums.MembershipStatus;
import com.ccapp.payments.subscription.domain.enums.SubscriptionGrade;
import com.ccapp.payments.subscription.repository.MembershipRepository;
import com.ccapp.payments.subscription.repository.SubscriptionPlanRepository;
import com.ccapp.platform.config.service.GlobalMlmConfigService;
import com.ccapp.platform.config.service.PlatformConfigurationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        MembershipService.class,
        CountryChartOfAccountsService.class,
        CountryConfigResolver.class,
        CountryConfigReadinessService.class,
        CountryConfigurationEngineService.class,
        PlatformConfigurationService.class,
        ConfigAuditMetadataService.class,
        TestJacksonAndCachingConfig.class
})
class MembershipServiceIntegrationTest {

    @Autowired
    private MembershipService membershipService;
    @Autowired
    private MembershipRepository membershipRepository;
    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;
    @Autowired
    private OperationRepository operationRepository;
    @Autowired
    private CountryRepository countryRepository;
    @Autowired
    private CountryConfigurationEngineService countryConfigurationEngineService;
    @Autowired
    private CountryChartOfAccountsService countryChartOfAccountsService;
    @MockBean
    private SubscriptionService subscriptionService;
    @MockBean
    private SubscriptionPlanCatalogService subscriptionPlanCatalogService;
    @MockBean
    private GlobalMlmConfigService globalMlmConfigService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        countryRepository.save(Country.builder()
                .id(UUID.randomUUID())
                .code("BJ")
                .name("Benin")
                .currency("XOF")
                .active(true)
                .configured(true)
                .fallback(false)
                .build());
        countryChartOfAccountsService.autoGenerateRequiredAccounts("BJ");

        ObjectNode membershipPayload = objectMapper.createObjectNode();
        ArrayNode plans = objectMapper.createArrayNode();
        ObjectNode plan = objectMapper.createObjectNode();
        plan.put("code", "BASIC");
        plan.put("name", "Basic");
        ObjectNode pricing = objectMapper.createObjectNode();
        pricing.put("amount", 2000);
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

    }

    @Test
    void activateForConfirmedOperation_shouldStoreSnapshotAndVersion() throws Exception {
        UUID accountHolderId = UUID.randomUUID();
        SubscriptionPlan plan = subscriptionPlanRepository.save(SubscriptionPlan.builder()
                .code("BASIC")
                .name("Basic")
                .grade(SubscriptionGrade.CLASSIC)
                .durationMonths(3)
                .country("BJ")
                .currency("XOF")
                .price(new BigDecimal("2000"))
                .active(true)
                .build());

        Operation operation = operationRepository.save(Operation.builder()
                .accountHolderId(accountHolderId)
                .actorUserId(accountHolderId)
                .type(OperationType.SUBSCRIPTION)
                .status(OperationStatus.CONFIRMED)
                .amount(new BigDecimal("2000"))
                .currency("XOF")
                .idempotencyKey("membership-it:" + UUID.randomUUID())
                .description("Subscription BASIC")
                .build());

        membershipService.createPendingMembership(operation.getId(), accountHolderId, plan);
        Membership activated = membershipService.activateForConfirmedOperation(operation);

        assertEquals(MembershipStatus.ACTIVE, activated.getStatus());
        assertNotNull(activated.getActivatedAt());
        assertNotNull(activated.getExpiresAt());
        assertEquals(90L, java.time.Duration.between(activated.getActivatedAt(), activated.getExpiresAt()).toDays());
        assertNotNull(activated.getConfigSnapshot());
        assertEquals(1, activated.getConfigVersion());
        assertEquals("WALLET", objectMapper.readTree(activated.getConfigSnapshot()).path("paymentProvider").asText());

        Membership persisted = membershipRepository.findByOperationId(operation.getId()).orElseThrow();
        assertEquals(MembershipStatus.ACTIVE, persisted.getStatus());
        assertNotNull(persisted.getConfigSnapshot());
    }

    @Test
    void activateForConfirmedOperation_shouldBeIdempotentForAlreadyActiveMembership() {
        UUID accountHolderId = UUID.randomUUID();
        SubscriptionPlan plan = subscriptionPlanRepository.save(SubscriptionPlan.builder()
                .code("CLASSIC_12M")
                .name("Classic 12 months")
                .grade(SubscriptionGrade.CLASSIC)
                .durationMonths(12)
                .country("BJ")
                .currency("XOF")
                .price(new BigDecimal("19000"))
                .active(true)
                .build());

        Operation operation = operationRepository.save(Operation.builder()
                .accountHolderId(accountHolderId)
                .actorUserId(accountHolderId)
                .type(OperationType.SUBSCRIPTION)
                .status(OperationStatus.CONFIRMED)
                .amount(new BigDecimal("19000"))
                .currency("XOF")
                .idempotencyKey("membership-idempotent:" + UUID.randomUUID())
                .description("Subscription CLASSIC_12M")
                .build());

        membershipService.createPendingMembership(operation.getId(), accountHolderId, plan);
        Membership firstActivation = membershipService.activateForConfirmedOperation(operation);
        OffsetDateTime firstActivatedAt = firstActivation.getActivatedAt();
        OffsetDateTime firstExpiresAt = firstActivation.getExpiresAt();
        String firstSnapshot = firstActivation.getConfigSnapshot();
        Integer firstVersion = firstActivation.getConfigVersion();

        Membership secondActivation = membershipService.activateForConfirmedOperation(operation);

        assertEquals(firstActivation.getId(), secondActivation.getId());
        assertEquals(MembershipStatus.ACTIVE, secondActivation.getStatus());
        assertEquals(firstActivatedAt, secondActivation.getActivatedAt());
        assertEquals(firstExpiresAt, secondActivation.getExpiresAt());
        assertEquals(firstSnapshot, secondActivation.getConfigSnapshot());
        assertEquals(firstVersion, secondActivation.getConfigVersion());
        assertEquals(1L, membershipRepository.count());
    }
}
