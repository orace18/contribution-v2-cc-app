package com.ccapp.country.config.service;

import com.ccapp.admin.service.ConfigAuditMetadataService;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.config.TestJacksonAndCachingConfig;
import com.ccapp.country.config.domain.entity.CountryConfigVersion;
import com.ccapp.country.config.domain.enums.CountryAccountSystemTag;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.dto.ActivateCountryConfigVersionRequest;
import com.ccapp.country.config.dto.CountryConfigReadinessResponse;
import com.ccapp.country.config.dto.UpsertCountryConfigRequest;
import com.ccapp.country.config.repository.CountryAccountSystemMappingRepository;
import com.ccapp.country.config.repository.CountryConfigVersionRepository;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.repository.CountryRepository;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.ledger.domain.entity.AccountingEvent;
import com.ccapp.ledger.repository.AccountingEventRepository;
import com.ccapp.ledger.repository.LedgerEntryRepository;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.repository.OperationRepository;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.payments.subscription.service.SubscriptionPlanCatalogService;
import com.ccapp.payments.subscription.service.SubscriptionService;
import com.ccapp.platform.config.domain.enums.PlatformConfigType;
import com.ccapp.platform.config.dto.UpsertPlatformConfigRequest;
import com.ccapp.platform.config.service.GlobalMlmConfigService;
import com.ccapp.platform.config.service.PlatformConfigurationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        CountryMasterDataBootstrapInitializer.class,
        CountrySetupBootstrapService.class,
        CountryChartOfAccountsService.class,
        CountryConfigurationEngineService.class,
        CountryConfigReadinessService.class,
        CountryConfigResolver.class,
        PlatformConfigurationService.class,
        AccountingPostingService.class,
        ConfigAuditMetadataService.class,
        TestJacksonAndCachingConfig.class
})
class BjActiveSubscriptionPostingSafetyRepairIntegrationTest {

    @Autowired
    private CountryRepository countryRepository;
    @Autowired
    private CountryConfigurationEngineService countryConfigurationEngineService;
    @Autowired
    private CountryConfigResolver countryConfigResolver;
    @Autowired
    private CountryConfigVersionRepository countryConfigVersionRepository;
    @Autowired
    private CountryAccountSystemMappingRepository countryAccountSystemMappingRepository;
    @Autowired
    private CountryChartOfAccountsService countryChartOfAccountsService;
    @Autowired
    private CountryMasterDataBootstrapInitializer countryMasterDataBootstrapInitializer;
    @Autowired
    private PlatformConfigurationService platformConfigurationService;
    @Autowired
    private AccountHolderRepository accountHolderRepository;
    @Autowired
    private OperationRepository operationRepository;
    @Autowired
    private AccountingPostingService accountingPostingService;
    @Autowired
    private AccountingEventRepository accountingEventRepository;
    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @MockBean
    private SubscriptionPlanCatalogService subscriptionPlanCatalogService;
    @MockBean
    private SubscriptionService subscriptionService;
    @MockBean
    private OperationService operationService;
    @MockBean
    private GlobalMlmConfigService globalMlmConfigService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void repairActiveSubscriptionPostingSafety_shouldPatchBjRuntimeConfigAndAllowPosting() throws Exception {
        saveCountry("BJ", "Benin");
        countryConfigurationEngineService.initializeCountryConfiguration("BJ", UUID.randomUUID());
        seedConfiguredBjSupportingSections();
        seedPlatformGeneralDefaults();
        saveInvalidActiveBjGeneralVersion(2, OffsetDateTime.now().minusMinutes(1));
        countryAccountSystemMappingRepository.findByCountryCodeAndSystemTag("BJ", CountryAccountSystemTag.MLM_POOL)
                .ifPresent(countryAccountSystemMappingRepository::delete);

        CountryConfigResolver.ResolvedConfig runtimeBefore = countryConfigResolver.resolveActiveConfig("BJ", CountryConfigType.GENERAL);
        assertEquals(CountryConfigResolver.ConfigSource.MERGED, runtimeBefore.source());
        assertEquals(2, runtimeBefore.version());
        assertTrue(countryConfigResolver.payloadAsObjectNode(runtimeBefore).path("percentages").isMissingNode()
                || countryConfigResolver.payloadAsObjectNode(runtimeBefore).path("percentages").path("commissionPool").isMissingNode());
        assertFalse(countryChartOfAccountsService.hasRequiredSystemMappings("BJ"));

        CountryConfigurationEngineService.ActiveSubscriptionPostingRepairResult result =
                countryConfigurationEngineService.repairActiveSubscriptionPostingSafety("BJ", UUID.randomUUID());

        assertEquals(2, result.activeGeneralVersionBefore());
        assertEquals(CountryConfigResolver.ConfigSource.MERGED, result.runtimeGeneralSourceBefore());
        assertTrue(result.defect().contains("commissionPool"));
        assertTrue(result.patched());
        assertEquals(3, result.activeGeneralVersionAfter());
        assertTrue(result.requiredSystemMappingsPresent());

        ObjectNode repairedCountryPayload = (ObjectNode) objectMapper.readTree(result.activeCountryGeneralPayloadAfter());
        assertEquals(60, repairedCountryPayload.path("percentages").path("commissionPool").asInt());
        assertEquals(15, repairedCountryPayload.path("percentages").path("guaranteeFund").asInt());
        assertEquals(10, repairedCountryPayload.path("percentages").path("platformFee").asInt());
        assertEquals(10, repairedCountryPayload.path("percentages").path("administration").asInt());
        assertEquals(5, repairedCountryPayload.path("percentages").path("socialFund").asInt());

        CountryConfigResolver.ResolvedConfig runtimeAfter = countryConfigResolver.resolveActiveConfig("BJ", CountryConfigType.GENERAL);
        assertEquals(3, runtimeAfter.version());
        assertEquals(60, countryConfigResolver.payloadAsObjectNode(runtimeAfter).path("percentages").path("commissionPool").asInt());
        assertTrue(countryChartOfAccountsService.hasRequiredSystemMappings("BJ"));
        assertNotNull(countryChartOfAccountsService.resolveSystemAccountCode("BJ", CountryAccountSystemTag.MLM_POOL));
        assertNotNull(countryChartOfAccountsService.resolveSystemAccountCode("BJ", CountryAccountSystemTag.PLATFORM_REVENUE));

        CountryConfigReadinessResponse readiness = countryConfigurationEngineService.getCountryReadiness("BJ");
        assertTrue(readiness.configured());
        assertFalse(readiness.missing().contains("general.subscription_allocations"));
        assertFalse(readiness.missing().contains("accounting.system_accounts"));

        UUID accountHolderId = UUID.randomUUID();
        accountHolderRepository.saveAndFlush(AccountHolder.builder()
                .id(accountHolderId)
                .firstName("Elie")
                .lastName("Houe")
                .phoneNumber("+2290102030405")
                .countryCode("BJ")
                .serviceCountryCode("BJ")
                .build());

        Operation operation = operationRepository.saveAndFlush(Operation.builder()
                .accountHolderId(accountHolderId)
                .actorUserId(accountHolderId)
                .type(OperationType.SUBSCRIPTION)
                .status(OperationStatus.CONFIRMED)
                .amount(new BigDecimal("19000.0000"))
                .currency("XOF")
                .idempotencyKey("bj-active-config-repair-" + UUID.randomUUID())
                .description("BJ subscription posting repair test")
                .build());
        when(operationService.getById(operation.getId())).thenReturn(operation);

        AccountingEvent event = accountingPostingService.postConfirmedOperation(operation.getId());

        assertNotNull(event.getId());
        assertEquals(1, accountingEventRepository.count());
        assertEquals(8, ledgerEntryRepository.count());
    }

    @Test
    void countryMasterDataBootstrapInitializer_shouldRepairBjRuntimeConfigOnStartup() throws Exception {
        saveCountry("BJ", "Benin");
        countryConfigurationEngineService.initializeCountryConfiguration("BJ", UUID.randomUUID());
        seedConfiguredBjSupportingSections();
        seedPlatformGeneralDefaults();
        saveInvalidActiveBjGeneralVersion(2, OffsetDateTime.now().minusMinutes(1));
        countryAccountSystemMappingRepository.findByCountryCodeAndSystemTag("BJ", CountryAccountSystemTag.MLM_POOL)
                .ifPresent(countryAccountSystemMappingRepository::delete);

        countryMasterDataBootstrapInitializer.run(new DefaultApplicationArguments(new String[0]));

        CountryConfigVersion activeGeneral = countryConfigVersionRepository
                .findFirstByCountryCodeAndConfigTypeAndEffectiveDateLessThanEqualOrderByEffectiveDateDescVersionDesc(
                        "BJ",
                        CountryConfigType.GENERAL,
                        OffsetDateTime.now()
                )
                .orElseThrow();
        ObjectNode payload = (ObjectNode) objectMapper.readTree(activeGeneral.getPayload());
        assertEquals(3, activeGeneral.getVersion());
        assertEquals(60, payload.path("percentages").path("commissionPool").asInt());
        assertTrue(countryChartOfAccountsService.hasRequiredSystemMappings("BJ"));
    }

    @Test
    void activateCountryConfigurationVersion_shouldStillRejectInvalidSubscriptionAllocations() {
        saveCountry("BJ", "Benin");
        countryConfigVersionRepository.saveAndFlush(CountryConfigVersion.builder()
                .countryCode("BJ")
                .configType(CountryConfigType.GENERAL)
                .version(5)
                .effectiveDate(OffsetDateTime.now().plusDays(1))
                .payload("{\"taxRate\":18}")
                .build());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> countryConfigurationEngineService.activateCountryConfigurationVersion(
                        "BJ",
                        CountryConfigType.GENERAL,
                        new ActivateCountryConfigVersionRequest(5, OffsetDateTime.now()),
                        UUID.randomUUID()
                ));

        assertTrue(exception.getMessage().contains("general.subscriptionAllocations"));
        assertTrue(exception.getMessage().contains("commissionPool"));
    }

    private void saveCountry(String code, String name) {
        countryRepository.saveAndFlush(Country.builder()
                .id(UUID.randomUUID())
                .code(code)
                .name(name)
                .currency("XOF")
                .active(true)
                .configured(false)
                .fallback(false)
                .build());
    }

    private void seedConfiguredBjSupportingSections() {
        UUID actorId = UUID.randomUUID();
        countryConfigurationEngineService.upsertCountryConfiguration(
                "BJ",
                CountryConfigType.ACCOUNTING,
                new UpsertCountryConfigRequest(buildAccountingPayload(), OffsetDateTime.now().minusMinutes(5)),
                actorId
        );
        countryConfigurationEngineService.upsertCountryConfiguration(
                "BJ",
                CountryConfigType.MEMBERSHIP,
                new UpsertCountryConfigRequest(buildMembershipPayload(), OffsetDateTime.now().minusMinutes(4)),
                actorId
        );
        countryConfigurationEngineService.upsertCountryConfiguration(
                "BJ",
                CountryConfigType.MLM_LIMITS,
                new UpsertCountryConfigRequest(buildMlmPayload(), OffsetDateTime.now().minusMinutes(3)),
                actorId
        );
        countryConfigurationEngineService.upsertCountryConfiguration(
                "BJ",
                CountryConfigType.GUARANTEE,
                new UpsertCountryConfigRequest(buildGuaranteePayload(), OffsetDateTime.now().minusMinutes(2)),
                actorId
        );
    }

    private void seedPlatformGeneralDefaults() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("taxRate", 18);
        ObjectNode walletSecurity = payload.putObject("walletSecurity");
        walletSecurity.put("pinRequired", true);
        walletSecurity.put("pinMaxAttempts", 5);
        walletSecurity.put("otpMaxAttempts", 3);
        walletSecurity.put("otpThreshold", 1000);
        walletSecurity.put("otpTtlSeconds", 300);
        platformConfigurationService.upsertPlatformConfiguration(
                PlatformConfigType.GLOBAL_GENERAL,
                new UpsertPlatformConfigRequest(payload, OffsetDateTime.now().minusMinutes(10)),
                UUID.randomUUID()
        );
    }

    private void saveInvalidActiveBjGeneralVersion(int version, OffsetDateTime effectiveDate) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("taxRate", 18);
        payload.put("marketplaceDefaultCommission", 5);
        countryConfigVersionRepository.saveAndFlush(CountryConfigVersion.builder()
                .countryCode("BJ")
                .configType(CountryConfigType.GENERAL)
                .version(version)
                .effectiveDate(effectiveDate)
                .payload(payload.toString())
                .build());
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

    private ObjectNode buildMembershipPayload() {
        ObjectNode payload = objectMapper.createObjectNode();
        ArrayNode plans = objectMapper.createArrayNode();
        plans.add(buildMembershipPlan("CLASSIC", 1, 1750));
        plans.add(buildMembershipPlan("CLASSIC", 3, 5000));
        plans.add(buildMembershipPlan("CLASSIC", 12, 19000));
        plans.add(buildMembershipPlan("GOLD", 1, 5750));
        plans.add(buildMembershipPlan("GOLD", 3, 17000));
        plans.add(buildMembershipPlan("GOLD", 12, 65000));
        plans.add(buildMembershipPlan("DIAMOND", 1, 12750));
        plans.add(buildMembershipPlan("DIAMOND", 3, 37250));
        plans.add(buildMembershipPlan("DIAMOND", 12, 145000));
        plans.add(buildMembershipPlan("FREE", 1, 0));
        payload.set("plans", plans);
        return payload;
    }

    private ObjectNode buildMembershipPlan(String formulaType, int durationMonths, int totalPrice) {
        ObjectNode plan = objectMapper.createObjectNode();
        plan.put("code", formulaType + "_" + durationMonths + "M");
        plan.put("tier", formulaType);
        plan.put("formulaType", formulaType);
        plan.put("name", "FREE".equals(formulaType) ? "Free" : formulaType + " " + durationMonths + " month");
        plan.put("durationMonths", durationMonths);
        plan.put("currency", "XOF");
        plan.put("totalPrice", totalPrice);
        plan.put("monthlyPrice", durationMonths == 0 ? 0 : totalPrice / durationMonths);
        plan.put("isActive", true);
        ObjectNode pricing = objectMapper.createObjectNode();
        pricing.put("amount", totalPrice);
        pricing.put("currency", "XOF");
        plan.set("pricing", pricing);
        plan.set("benefits", objectMapper.createArrayNode().add(formulaType + " benefit"));
        return plan;
    }

    private ObjectNode buildMlmPayload() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("maxDirectChildren", 3);
        payload.put("weeklyCap", 50000);
        payload.set("weeklyCapsByTier", objectMapper.createArrayNode()
                .add(buildTierCap("CLASSIC", 10000))
                .add(buildTierCap("GOLD", 20000))
                .add(buildTierCap("DIAMOND", 30000)));
        return payload;
    }

    private ObjectNode buildGuaranteePayload() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("globalMaxExposure", 15000);
        payload.set("limitsByTier", objectMapper.createArrayNode()
                .add(buildGuaranteeTier("CLASSIC", 2000, 1500))
                .add(buildGuaranteeTier("GOLD", 3000, 2500))
                .add(buildGuaranteeTier("DIAMOND", 4000, 3500)));
        return payload;
    }

    private ObjectNode buildTierCap(String tier, int cap) {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("tier", tier);
        item.put("cap", cap);
        return item;
    }

    private ObjectNode buildGuaranteeTier(String tier, int maxAmount, int maxExposure) {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("tier", tier);
        item.put("maxAmount", maxAmount);
        item.put("maxExposure", maxExposure);
        return item;
    }
}
