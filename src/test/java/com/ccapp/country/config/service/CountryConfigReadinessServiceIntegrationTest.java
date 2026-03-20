package com.ccapp.country.config.service;

import com.ccapp.admin.service.ConfigAuditMetadataService;
import com.ccapp.admin.domain.entity.AdminUser;
import com.ccapp.admin.domain.enums.AdminRole;
import com.ccapp.admin.domain.enums.AdminUserStatus;
import com.ccapp.admin.repository.AdminUserRepository;
import com.ccapp.config.TestJacksonAndCachingConfig;
import com.ccapp.country.config.domain.entity.CountryChartAccount;
import com.ccapp.country.config.domain.enums.CountryChartAccountStatus;
import com.ccapp.country.config.domain.enums.CountryChartAccountType;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.dto.CountryConfigReadinessResponse;
import com.ccapp.country.config.dto.UpsertCountryConfigRequest;
import com.ccapp.country.config.repository.CountryChartAccountRepository;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.repository.CountryRepository;
import com.ccapp.platform.config.service.PlatformConfigurationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        CountryChartOfAccountsService.class,
        CountryConfigReadinessService.class,
        CountryConfigResolver.class,
        CountryConfigurationEngineService.class,
        PlatformConfigurationService.class,
        ConfigAuditMetadataService.class,
        TestJacksonAndCachingConfig.class
})
class CountryConfigReadinessServiceIntegrationTest {

    @Autowired
    private CountryRepository countryRepository;
    @Autowired
    private CountryChartAccountRepository countryChartAccountRepository;
    @Autowired
    private AdminUserRepository adminUserRepository;
    @Autowired
    private CountryConfigurationEngineService countryConfigurationEngineService;
    @Autowired
    private CountryConfigReadinessService countryConfigReadinessService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void evaluateReadiness_shouldExposeMissingItemsWhenCountryIsPartial() {
        saveCountry("BJ", "Benin");

        CountryConfigReadinessResponse readiness = countryConfigReadinessService.evaluateReadiness("BJ");

        assertFalse(readiness.configured());
        assertTrue(readiness.score() < 100);
        assertTrue(readiness.missing().contains("membership.gold_plan"));
        assertTrue(readiness.missing().contains("accounting.chart_of_accounts"));
        assertTrue(readiness.missing().contains("rbac.country_ops"));
    }

    @Test
    void evaluateReadiness_shouldReturnReadyWhenMandatorySectionsPass() {
        Country country = saveCountry("CI", "Cote d'Ivoire");
        saveChartAccount("CI", "ACC-GUARANTEE", "Guarantee Pool");
        saveChartAccount("CI", "ACC-ADMIN", "Admin Pool");
        saveChartAccount("CI", "ACC-PLATFORM", "Platform Pool");
        saveChartAccount("CI", "ACC-COMMISSION", "Commission Pool");
        saveChartAccount("CI", "ACC-SOCIAL", "Social Fund");
        saveCountryAdmin(country);

        countryConfigurationEngineService.upsertCountryConfiguration(
                "CI",
                CountryConfigType.MEMBERSHIP,
                new UpsertCountryConfigRequest(buildMembershipPayload(), OffsetDateTime.now()),
                UUID.randomUUID()
        );
        countryConfigurationEngineService.upsertCountryConfiguration(
                "CI",
                CountryConfigType.MLM_LIMITS,
                new UpsertCountryConfigRequest(buildMlmPayload(), OffsetDateTime.now()),
                UUID.randomUUID()
        );
        countryConfigurationEngineService.upsertCountryConfiguration(
                "CI",
                CountryConfigType.GUARANTEE,
                new UpsertCountryConfigRequest(buildGuaranteePayload(), OffsetDateTime.now()),
                UUID.randomUUID()
        );
        countryConfigurationEngineService.upsertCountryConfiguration(
                "CI",
                CountryConfigType.ACCOUNTING,
                new UpsertCountryConfigRequest(buildAccountingPayload(), OffsetDateTime.now()),
                UUID.randomUUID()
        );
        countryConfigurationEngineService.upsertCountryConfiguration(
                "CI",
                CountryConfigType.FINANCING,
                new UpsertCountryConfigRequest(objectMapper.createObjectNode().put("enabled", false), OffsetDateTime.now()),
                UUID.randomUUID()
        );
        countryConfigurationEngineService.upsertCountryConfiguration(
                "CI",
                CountryConfigType.PAYMENT,
                new UpsertCountryConfigRequest(objectMapper.createObjectNode().put("enabled", false), OffsetDateTime.now()),
                UUID.randomUUID()
        );

        CountryConfigReadinessResponse readiness = countryConfigReadinessService.evaluateReadiness("CI");
        assertTrue(readiness.configured());
        assertEquals(100, readiness.score());
        assertTrue(readiness.missing().isEmpty());
    }

    private Country saveCountry(String code, String name) {
        return countryRepository.save(Country.builder()
                .id(UUID.randomUUID())
                .code(code)
                .name(name)
                .currency("XOF")
                .active(true)
                .configured(false)
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

    private void saveCountryAdmin(Country country) {
        adminUserRepository.save(AdminUser.builder()
                .id(UUID.randomUUID())
                .email("country-admin@" + country.getCode().toLowerCase() + ".test")
                .passwordHash("dummy-hash")
                .role(AdminRole.ROLE_COUNTRY_ADMIN)
                .countryId(country.getId())
                .status(AdminUserStatus.ACTIVE)
                .twoFactorEnabled(true)
                .twoFactorSecret("SECRET")
                .build());
    }

    private ObjectNode buildMembershipPayload() {
        ObjectNode payload = objectMapper.createObjectNode();
        ArrayNode plans = objectMapper.createArrayNode();
        plans.add(buildPlan("CLASSIC", 2000));
        plans.add(buildPlan("GOLD", 4000));
        plans.add(buildPlan("DIAMOND", 6000));
        payload.set("plans", plans);
        return payload;
    }

    private ObjectNode buildPlan(String tier, int amount) {
        ObjectNode plan = objectMapper.createObjectNode();
        plan.put("code", tier);
        plan.put("name", tier);
        plan.put("tier", tier);
        ObjectNode pricing = objectMapper.createObjectNode();
        pricing.put("amount", amount);
        pricing.put("currency", "XOF");
        plan.set("pricing", pricing);
        plan.set("benefits", objectMapper.createArrayNode().add("Benefit " + tier));
        return plan;
    }

    private ObjectNode buildMlmPayload() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("unilevel", 12);
        payload.put("binary", 8);
        ArrayNode caps = objectMapper.createArrayNode();
        caps.add(buildTierCap("CLASSIC", 5000));
        caps.add(buildTierCap("GOLD", 8000));
        caps.add(buildTierCap("DIAMOND", 10000));
        payload.set("weeklyCapsByTier", caps);
        return payload;
    }

    private ObjectNode buildTierCap(String tier, int cap) {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("tier", tier);
        item.put("cap", cap);
        return item;
    }

    private ObjectNode buildGuaranteePayload() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("globalMaxExposure", 10000);
        ObjectNode riskLimits = objectMapper.createObjectNode();
        riskLimits.put("maxDefaultRate", 15);
        payload.set("riskLimits", riskLimits);
        ArrayNode limits = objectMapper.createArrayNode();
        limits.add(buildGuaranteeTier("CLASSIC", 4000, 3000));
        limits.add(buildGuaranteeTier("GOLD", 7000, 5000));
        limits.add(buildGuaranteeTier("DIAMOND", 9000, 7000));
        payload.set("limitsByTier", limits);
        return payload;
    }

    private ObjectNode buildGuaranteeTier(String tier, int maxAmount, int maxExposure) {
        ObjectNode tierNode = objectMapper.createObjectNode();
        tierNode.put("tier", tier);
        tierNode.put("maxAmount", maxAmount);
        tierNode.put("maxExposure", maxExposure);
        return tierNode;
    }

    private ObjectNode buildAccountingPayload() {
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode accounts = objectMapper.createObjectNode();
        accounts.put("guaranteePoolAccount", "ACC-GUARANTEE");
        accounts.put("adminPoolAccount", "ACC-ADMIN");
        accounts.put("platformPoolAccount", "ACC-PLATFORM");
        accounts.put("commissionPoolAccount", "ACC-COMMISSION");
        accounts.put("socialFundAccount", "ACC-SOCIAL");
        payload.set("accounts", accounts);
        return payload;
    }
}
