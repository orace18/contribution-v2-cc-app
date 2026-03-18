package com.ccapp.country.config.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.config.TestJacksonAndCachingConfig;
import com.ccapp.country.config.domain.entity.CountryChartAccount;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        CountryConfigurationEngineService.class,
        CountryConfigReadinessService.class,
        CountryConfigResolver.class,
        TestJacksonAndCachingConfig.class
})
class CountryConfigurationEngineServiceIntegrationTest {

    @Autowired
    private CountryConfigurationEngineService countryConfigurationEngineService;
    @Autowired
    private CountryRepository countryRepository;
    @Autowired
    private CountryChartAccountRepository countryChartAccountRepository;
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
        ObjectNode accounts = objectMapper.createObjectNode();
        accounts.put("loanDisbursement", "ACC-1100");
        accounts.put("fees", "ACC-2100");
        accounts.put("reserves", "ACC-3100");
        accounts.put("penalties", "ACC-4100");
        accountingPayload.set("accounts", accounts);
        saveChartAccount("TG", "ACC-1100", "Loan Disbursement");
        saveChartAccount("TG", "ACC-2100", "Fees");
        saveChartAccount("TG", "ACC-3100", "Reserves");
        saveChartAccount("TG", "ACC-4100", "Penalties");

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
        assertEquals("ACC-1100", full.accounting().get("accounts").get("loanDisbursement").asText());
        assertEquals("Basic", full.membership().get("plans").get(0).get("name").asText());
        assertTrue(full.general().isObject());
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
}
