package com.ccapp.country.config.service;

import com.ccapp.admin.service.ConfigAuditMetadataService;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.config.TestJacksonAndCachingConfig;
import com.ccapp.country.config.domain.entity.CountryChartAccount;
import com.ccapp.country.config.domain.enums.CountryAccountGroup;
import com.ccapp.country.config.domain.enums.CountryChartAccountStatus;
import com.ccapp.country.config.domain.enums.CountryChartAccountType;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.dto.ActivateCountryConfigVersionRequest;
import com.ccapp.country.config.dto.CountryConfigFullResponse;
import com.ccapp.country.config.dto.CountryConfigSnapshotResponse;
import com.ccapp.country.config.dto.CountryConfigVersionHistoryItemResponse;
import com.ccapp.country.config.dto.CountryConfigVersionResponse;
import com.ccapp.country.config.dto.UpsertCountryConfigRequest;
import com.ccapp.country.config.repository.CountryChartAccountRepository;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.repository.CountryRepository;
import com.ccapp.payments.subscription.service.SubscriptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        CountryChartOfAccountsService.class,
        CountryConfigurationEngineService.class,
        CountryConfigReadinessService.class,
        CountryConfigResolver.class,
        ConfigAuditMetadataService.class,
        TestJacksonAndCachingConfig.class
})
class CountryConfigurationEngineServiceIntegrationTest {

    @Autowired
    private CountryConfigurationEngineService countryConfigurationEngineService;
    @Autowired
    private CountryRepository countryRepository;
    @Autowired
    private CountryChartAccountRepository countryChartAccountRepository;
    @MockBean
    private SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void upsertAndSnapshot_shouldVersionGuaranteeConfigByEffectiveDate() {
        saveCountry("BJ", "Benin");

        ObjectNode firstPayload = objectMapper.createObjectNode()
                .put("globalMaxExposure", 1000);
        CountryConfigVersionResponse version1 = countryConfigurationEngineService.upsertCountryConfiguration(
                "bj",
                CountryConfigType.GUARANTEE,
                new UpsertCountryConfigRequest(firstPayload, OffsetDateTime.now().minusDays(1)),
                UUID.randomUUID()
        );
        assertEquals(1, version1.version());

        ObjectNode secondPayload = objectMapper.createObjectNode()
                .put("globalMaxExposure", 2500);
        CountryConfigVersionResponse version2 = countryConfigurationEngineService.upsertCountryConfiguration(
                "BJ",
                CountryConfigType.GUARANTEE,
                new UpsertCountryConfigRequest(secondPayload, OffsetDateTime.now().plusDays(2)),
                UUID.randomUUID()
        );
        assertEquals(2, version2.version());

        CountryConfigSnapshotResponse snapshotNow = countryConfigurationEngineService
                .getCountryConfiguration("BJ", OffsetDateTime.now());
        var guaranteeNow = snapshotNow.configs().stream()
                .filter(item -> item.configType() == CountryConfigType.GUARANTEE)
                .findFirst()
                .orElseThrow();
        assertEquals(1, guaranteeNow.version());
        assertEquals(1000, guaranteeNow.payload().get("globalMaxExposure").asInt());

        CountryConfigSnapshotResponse snapshotFuture = countryConfigurationEngineService
                .getCountryConfiguration("BJ", OffsetDateTime.now().plusDays(3));
        var guaranteeFuture = snapshotFuture.configs().stream()
                .filter(item -> item.configType() == CountryConfigType.GUARANTEE)
                .findFirst()
                .orElseThrow();
        assertEquals(2, guaranteeFuture.version());
        assertEquals(2500, guaranteeFuture.payload().get("globalMaxExposure").asInt());

        assertTrue(countryConfigurationEngineService.hasEffectiveConfiguration("BJ", CountryConfigType.GUARANTEE));
    }

    @Test
    void upsertGeneral_shouldRejectNonBooleanFeatureFlags() {
        saveCountry("CI", "Cote d'Ivoire");

        ObjectNode invalidGeneral = objectMapper.createObjectNode();
        ObjectNode features = objectMapper.createObjectNode();
        features.put("chatEnabled", true);
        features.put("maxPartners", 20);
        invalidGeneral.set("features", features);

        assertThrows(BusinessException.class, () -> countryConfigurationEngineService.upsertCountryConfiguration(
                "CI",
                CountryConfigType.GENERAL,
                new UpsertCountryConfigRequest(invalidGeneral, OffsetDateTime.now()),
                UUID.randomUUID()
        ));
    }

    @Test
    void upsertMembership_shouldRejectNonPositivePricing() {
        saveCountry("GN", "Guinea");

        ObjectNode membershipPayload = objectMapper.createObjectNode();
        ArrayNode plans = objectMapper.createArrayNode();
        ObjectNode plan = objectMapper.createObjectNode();
        plan.put("code", "BASIC");
        plan.put("name", "Basic");
        plan.put("tier", "BASIC");
        ObjectNode pricing = objectMapper.createObjectNode();
        pricing.put("amount", 0);
        pricing.put("currency", "XOF");
        plan.set("pricing", pricing);
        plan.set("benefits", objectMapper.createArrayNode().add("Community"));
        plans.add(plan);
        membershipPayload.set("plans", plans);

        assertThrows(BusinessException.class, () -> countryConfigurationEngineService.upsertCountryConfiguration(
                "GN",
                CountryConfigType.MEMBERSHIP,
                new UpsertCountryConfigRequest(membershipPayload, OffsetDateTime.now()),
                UUID.randomUUID()
        ));
    }

    @Test
    void initializeAndActivate_shouldPrepareEmptyConfigsAndActivateHistoricalVersion() {
        countryRepository.save(Country.builder()
                .id(UUID.randomUUID())
                .code("SN")
                .name("Senegal")
                .currency("XOF")
                .active(false)
                .configured(false)
                .fallback(false)
                .build());

        CountryConfigSnapshotResponse initialized = countryConfigurationEngineService
                .initializeCountryConfiguration("SN", UUID.randomUUID());
        assertEquals("SN", initialized.countryCode());
        assertEquals(CountryConfigType.values().length, initialized.configs().size());
        assertEquals((long) CountryAccountGroup.defaultInitializationGroups().size(),
                countryChartAccountRepository.findByCountryCodeOrderByAccountCodeAsc("SN").stream()
                .filter(account -> CountryAccountGroup.defaultInitializationGroups().contains(account.getAccountGroup()))
                .count());

        Country updatedCountry = countryRepository.findByCode("SN").orElseThrow();
        assertTrue(updatedCountry.isActive());
        assertEquals(false, updatedCountry.isConfigured());

        ObjectNode guaranteePayloadV2 = objectMapper.createObjectNode()
                .put("globalMaxExposure", 100000);
        CountryConfigVersionResponse guaranteeV2 = countryConfigurationEngineService.upsertCountryConfiguration(
                "SN",
                CountryConfigType.GUARANTEE,
                new UpsertCountryConfigRequest(guaranteePayloadV2, OffsetDateTime.now().plusDays(2)),
                UUID.randomUUID()
        );

        ObjectNode guaranteePayloadV3 = objectMapper.createObjectNode()
                .put("globalMaxExposure", 50000);
        CountryConfigVersionResponse guaranteeV3 = countryConfigurationEngineService.upsertCountryConfiguration(
                "SN",
                CountryConfigType.GUARANTEE,
                new UpsertCountryConfigRequest(guaranteePayloadV3, OffsetDateTime.now().plusDays(4)),
                UUID.randomUUID()
        );
        assertTrue(guaranteeV3.version() > guaranteeV2.version());

        CountryConfigVersionResponse activated = countryConfigurationEngineService.activateCountryConfigurationVersion(
                "SN",
                CountryConfigType.GUARANTEE,
                new ActivateCountryConfigVersionRequest(guaranteeV2.version(), OffsetDateTime.now()),
                UUID.randomUUID()
        );
        assertEquals(guaranteePayloadV2.get("globalMaxExposure").asInt(), activated.payload().get("globalMaxExposure").asInt());

        List<CountryConfigVersionHistoryItemResponse> versions = countryConfigurationEngineService
                .listCountryConfigurationVersions("SN", CountryConfigType.GUARANTEE);
        assertTrue(versions.size() >= 4);
        assertTrue(versions.stream().anyMatch(CountryConfigVersionHistoryItemResponse::active));
    }

    @Test
    void fullConfig_shouldReturnAggregatedPayloadsByDomain() {
        saveCountry("TG", "Togo");

        ObjectNode accountingPayload = objectMapper.createObjectNode();
        accountingPayload.put("mode", "AUTO_GENERATED");
        accountingPayload.put("masterTemplate", "UNIVERSAL_MASTER");
        ArrayNode requiredSystemTags = objectMapper.createArrayNode();
        requiredSystemTags.add("USER_WALLET");
        requiredSystemTags.add("PLATFORM_REVENUE");
        requiredSystemTags.add("MLM_POOL");
        requiredSystemTags.add("CIRCLE_POOL");
        requiredSystemTags.add("LOAN_PORTFOLIO");
        requiredSystemTags.add("INTEREST_REVENUE");
        requiredSystemTags.add("PSP_CLEARING");
        accountingPayload.set("requiredSystemTags", requiredSystemTags);

        countryConfigurationEngineService.upsertCountryConfiguration(
                "TG",
                CountryConfigType.ACCOUNTING,
                new UpsertCountryConfigRequest(accountingPayload, OffsetDateTime.now()),
                UUID.randomUUID()
        );

        ObjectNode membershipPayload = objectMapper.createObjectNode();
        ArrayNode plans = objectMapper.createArrayNode();
        ObjectNode basicPlan = objectMapper.createObjectNode();
        basicPlan.put("code", "BASIC");
        basicPlan.put("name", "Basic");
        basicPlan.put("tier", "BASIC");
        ObjectNode pricing = objectMapper.createObjectNode();
        pricing.put("amount", 2000);
        pricing.put("currency", "XOF");
        basicPlan.set("pricing", pricing);
        basicPlan.set("benefits", objectMapper.createArrayNode().add("Community chat"));
        plans.add(basicPlan);
        membershipPayload.set("plans", plans);

        countryConfigurationEngineService.upsertCountryConfiguration(
                "TG",
                CountryConfigType.MEMBERSHIP,
                new UpsertCountryConfigRequest(membershipPayload, OffsetDateTime.now()),
                UUID.randomUUID()
        );

        CountryConfigFullResponse full = countryConfigurationEngineService.getFullCountryConfiguration("TG", OffsetDateTime.now());
        assertEquals("AUTO_GENERATED", full.accounting().get("mode").asText());
        assertEquals("UNIVERSAL_MASTER", full.accounting().get("masterTemplate").asText());
        assertEquals("Basic", full.membership().get("plans").get(0).get("name").asText());
        assertTrue(full.general().isObject());
    }

    @Test
    void validateCountrySetup_shouldReturnCrossSectionErrorsWithoutPersisting() {
        saveCountry("BJ", "Benin");
        countryConfigurationEngineService.initializeCountryConfiguration("BJ", UUID.randomUUID());

        ObjectNode generalPayload = objectMapper.createObjectNode();
        generalPayload.put("taxRate", 18);
        generalPayload.put("marketplaceDefaultCommission", 5);
        ObjectNode circle = objectMapper.createObjectNode();
        circle.put("minMembers", 5);
        circle.put("maxMembers", 50);
        circle.put("maxAssistedMembers", 10);
        generalPayload.set("circle", circle);
        ObjectNode features = objectMapper.createObjectNode();
        features.put("chatEnabled", true);
        features.put("marketplaceEnabled", true);
        features.put("financingEnabled", true);
        features.put("loanEnabled", true);
        generalPayload.set("features", features);

        ObjectNode accountingPayload = objectMapper.createObjectNode();
        accountingPayload.put("mode", "AUTO_GENERATED");
        accountingPayload.put("masterTemplate", "UNIVERSAL_MASTER");
        accountingPayload.set("requiredSystemTags", objectMapper.createArrayNode()
                .add("USER_WALLET")
                .add("PLATFORM_REVENUE")
                .add("MLM_POOL")
                .add("CIRCLE_POOL")
                .add("LOAN_PORTFOLIO")
                .add("INTEREST_REVENUE")
                .add("PSP_CLEARING"));

        ObjectNode membershipPayload = objectMapper.createObjectNode();
        ArrayNode membershipPlans = objectMapper.createArrayNode();
        membershipPlans.add(buildMembershipPlan("CLASSIC", 1, 1000));
        membershipPlans.add(buildMembershipPlan("CLASSIC", 3, 3000));
        membershipPlans.add(buildMembershipPlan("CLASSIC", 12, 12000));
        membershipPlans.add(buildMembershipPlan("GOLD", 1, 5000));
        membershipPlans.add(buildMembershipPlan("GOLD", 3, 15000));
        membershipPlans.add(buildMembershipPlan("GOLD", 12, 60000));
        membershipPlans.add(buildMembershipPlan("DIAMOND", 1, 10000));
        membershipPlans.add(buildMembershipPlan("DIAMOND", 3, 30000));
        membershipPlans.add(buildMembershipPlan("DIAMOND", 12, 120000));
        membershipPlans.add(buildMembershipPlan("FREE", 1, 0));
        membershipPayload.set("plans", membershipPlans);

        ObjectNode mlmPayload = objectMapper.createObjectNode();
        mlmPayload.put("maxDirectChildren", 3);
        mlmPayload.put("weeklyCap", 50000);
        mlmPayload.set("weeklyCapsByTier", objectMapper.createArrayNode()
                .add(buildTierCap("CLASSIC", 10000))
                .add(buildTierCap("GOLD", 20000))
                .add(buildTierCap("DIAMOND", 30000)));

        ObjectNode financingPayload = objectMapper.createObjectNode();
        financingPayload.put("enabled", true);
        financingPayload.put("minAmount", 100);
        financingPayload.put("maxAmount", 10000);
        financingPayload.put("maxInterestRate", 25);
        ArrayNode offers = objectMapper.createArrayNode();
        ObjectNode standardOffer = objectMapper.createObjectNode();
        standardOffer.put("code", "STANDARD");
        standardOffer.put("minAmount", 100);
        standardOffer.put("maxAmount", 10000);
        standardOffer.put("maxInterestRate", 25);
        standardOffer.set("eligibleTiers", objectMapper.createArrayNode()
                .add("CLASSIC")
                .add("GOLD")
                .add("DIAMOND"));
        offers.add(standardOffer);
        financingPayload.set("offers", offers);

        ObjectNode guaranteePayload = objectMapper.createObjectNode();
        guaranteePayload.put("globalMaxExposure", 15000);
        guaranteePayload.set("limitsByTier", objectMapper.createArrayNode()
                .add(buildGuaranteeTier("CLASSIC", 2000, 1500))
                .add(buildGuaranteeTier("GOLD", 3000, 2500))
                .add(buildGuaranteeTier("DIAMOND", 4000, 3500)));

        var response = countryConfigurationEngineService.validateCountrySetup(
                "BJ",
                Map.of(
                        CountryConfigType.GENERAL, generalPayload,
                        CountryConfigType.ACCOUNTING, accountingPayload,
                        CountryConfigType.MEMBERSHIP, membershipPayload,
                        CountryConfigType.MLM_LIMITS, mlmPayload,
                        CountryConfigType.FINANCING, financingPayload,
                        CountryConfigType.GUARANTEE, guaranteePayload
                ),
                OffsetDateTime.now()
        );

        assertFalse(response.valid());
        assertEquals(1, response.errors().size());
        assertEquals("guarantee", response.errors().get(0).field());
        assertEquals("Guarantee must align with financing rules", response.errors().get(0).message());
    }

    @Test
    void validateCountrySetup_shouldMarkBjAndIntConfiguredWithoutFinancing() {
        for (String countryCode : List.of("BJ", "INT")) {
            saveCountry(countryCode, countryCode.equals("BJ") ? "Benin" : "Integration Test");
            countryConfigurationEngineService.initializeCountryConfiguration(countryCode, UUID.randomUUID());

            var beforeValidation = countryRepository.findByCode(countryCode).orElseThrow();
            assertFalse(beforeValidation.isConfigured());

            var response = countryConfigurationEngineService.validateCountrySetup(
                    countryCode,
                    Map.of(
                            CountryConfigType.GENERAL, objectMapper.createObjectNode(),
                            CountryConfigType.ACCOUNTING, buildAccountingPayload(),
                            CountryConfigType.MEMBERSHIP, buildCompleteMembershipPayload(),
                            CountryConfigType.MLM_LIMITS, buildMlmPayload(),
                            CountryConfigType.GUARANTEE, buildGuaranteePayload()
                    ),
                    OffsetDateTime.now()
            );

            assertTrue(response.valid());
            assertTrue(response.errors().isEmpty());
            assertTrue(countryConfigurationEngineService.getCountryReadiness(countryCode).configured());
            assertEquals(
                    com.ccapp.country.config.dto.CountryConfigReadinessStatus.CONFIGURED,
                    countryConfigurationEngineService.getCountryReadiness(countryCode).status()
            );
            assertTrue(countryRepository.findByCode(countryCode).orElseThrow().isConfigured());
        }
    }

    private void saveCountry(String code, String name) {
        countryRepository.save(Country.builder()
                .id(UUID.randomUUID())
                .code(code)
                .name(name)
                .currency("XOF")
                .active(true)
                .configured(true)
                .fallback(false)
                .build());
    }

    private void saveChartAccount(String countryCode, String accountCode, String accountName) {
        countryChartAccountRepository.save(CountryChartAccount.builder()
                .countryCode(countryCode)
                .accountCode(accountCode)
                .accountName(accountName)
                .accountType(CountryChartAccountType.ASSET)
                .currency("XOF")
                .openingBalance(BigDecimal.ZERO)
                .status(CountryChartAccountStatus.ACTIVE)
                .build());
    }

    private ObjectNode buildMembershipPlan(String formulaType, int durationMonths, int totalPrice) {
        ObjectNode plan = objectMapper.createObjectNode();
        plan.put("code", formulaType);
        plan.put("tier", formulaType);
        plan.put("formulaType", formulaType);
        plan.put("name", formulaType + (durationMonths == 1 ? "" : " " + durationMonths + "M"));
        plan.put("durationMonths", durationMonths);
        ObjectNode pricing = objectMapper.createObjectNode();
        pricing.put("amount", totalPrice);
        pricing.put("currency", "XOF");
        plan.set("pricing", pricing);
        plan.put("currency", "XOF");
        plan.put("totalPrice", totalPrice);
        plan.put("monthlyPrice", durationMonths == 0 ? 0 : totalPrice / durationMonths);
        plan.put("isActive", true);
        plan.set("benefits", objectMapper.createArrayNode().add(formulaType + " benefit"));
        return plan;
    }

    private ObjectNode buildCompleteMembershipPayload() {
        ObjectNode membershipPayload = objectMapper.createObjectNode();
        ArrayNode membershipPlans = objectMapper.createArrayNode();
        membershipPlans.add(buildMembershipPlan("CLASSIC", 1, 1000));
        membershipPlans.add(buildMembershipPlan("CLASSIC", 3, 3000));
        membershipPlans.add(buildMembershipPlan("CLASSIC", 12, 12000));
        membershipPlans.add(buildMembershipPlan("GOLD", 1, 5000));
        membershipPlans.add(buildMembershipPlan("GOLD", 3, 15000));
        membershipPlans.add(buildMembershipPlan("GOLD", 12, 60000));
        membershipPlans.add(buildMembershipPlan("DIAMOND", 1, 10000));
        membershipPlans.add(buildMembershipPlan("DIAMOND", 3, 30000));
        membershipPlans.add(buildMembershipPlan("DIAMOND", 12, 120000));
        membershipPlans.add(buildMembershipPlan("FREE", 1, 0));
        membershipPayload.set("plans", membershipPlans);
        return membershipPayload;
    }

    private ObjectNode buildAccountingPayload() {
        ObjectNode accountingPayload = objectMapper.createObjectNode();
        accountingPayload.put("mode", "AUTO_GENERATED");
        accountingPayload.put("masterTemplate", "UNIVERSAL_MASTER");
        accountingPayload.set("requiredSystemTags", objectMapper.createArrayNode()
                .add("USER_WALLET")
                .add("PLATFORM_REVENUE")
                .add("MLM_POOL")
                .add("CIRCLE_POOL")
                .add("LOAN_PORTFOLIO")
                .add("INTEREST_REVENUE")
                .add("PSP_CLEARING"));
        return accountingPayload;
    }

    private ObjectNode buildMlmPayload() {
        ObjectNode mlmPayload = objectMapper.createObjectNode();
        mlmPayload.put("maxDirectChildren", 3);
        mlmPayload.put("weeklyCap", 50000);
        mlmPayload.set("weeklyCapsByTier", objectMapper.createArrayNode()
                .add(buildTierCap("CLASSIC", 10000))
                .add(buildTierCap("GOLD", 20000))
                .add(buildTierCap("DIAMOND", 30000)));
        return mlmPayload;
    }

    private ObjectNode buildGuaranteePayload() {
        ObjectNode guaranteePayload = objectMapper.createObjectNode();
        guaranteePayload.put("globalMaxExposure", 15000);
        guaranteePayload.set("limitsByTier", objectMapper.createArrayNode()
                .add(buildGuaranteeTier("CLASSIC", 2000, 1500))
                .add(buildGuaranteeTier("GOLD", 3000, 2500))
                .add(buildGuaranteeTier("DIAMOND", 4000, 3500)));
        return guaranteePayload;
    }

    private ObjectNode buildTierCap(String tier, int amount) {
        ObjectNode cap = objectMapper.createObjectNode();
        cap.put("tier", tier);
        cap.put("cap", amount);
        return cap;
    }

    private ObjectNode buildGuaranteeTier(String tier, int maxAmount, int maxExposure) {
        ObjectNode guaranteeTier = objectMapper.createObjectNode();
        guaranteeTier.put("tier", tier);
        guaranteeTier.put("maxAmount", maxAmount);
        guaranteeTier.put("maxExposure", maxExposure);
        return guaranteeTier;
    }
}
