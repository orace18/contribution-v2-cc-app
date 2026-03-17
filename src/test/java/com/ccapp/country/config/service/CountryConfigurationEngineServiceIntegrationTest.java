package com.ccapp.country.config.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.config.TestJacksonAndCachingConfig;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.dto.ActivateCountryConfigVersionRequest;
import com.ccapp.country.config.dto.CountryConfigFullResponse;
import com.ccapp.country.config.dto.CountryConfigSnapshotResponse;
import com.ccapp.country.config.dto.CountryConfigVersionHistoryItemResponse;
import com.ccapp.country.config.dto.CountryConfigVersionResponse;
import com.ccapp.country.config.dto.UpsertCountryConfigRequest;
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
@Import({CountryConfigurationEngineService.class, TestJacksonAndCachingConfig.class})
class CountryConfigurationEngineServiceIntegrationTest {

    @Autowired
    private CountryConfigurationEngineService countryConfigurationEngineService;
    @Autowired
    private CountryRepository countryRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void upsertAndSnapshot_shouldVersionConfigsAndResolveByEffectiveDate() {
        countryRepository.save(Country.builder()
                .id(UUID.randomUUID())
                .code("BJ")
                .name("Benin")
                .currency("XOF")
                .active(true)
                .configured(true)
                .fallback(false)
                .build());

        ObjectNode firstPayload = objectMapper.createObjectNode()
                .put("maxLoanAmount", 1000)
                .put("minimumScore", 55);
        CountryConfigVersionResponse version1 = countryConfigurationEngineService.upsertCountryConfiguration(
                "bj",
                CountryConfigType.RISK_CONFIG,
                new UpsertCountryConfigRequest(firstPayload, OffsetDateTime.now().minusDays(1)),
                UUID.randomUUID()
        );
        assertEquals(1, version1.version());

        ObjectNode secondPayload = objectMapper.createObjectNode()
                .put("maxLoanAmount", 2500)
                .put("minimumScore", 60);
        CountryConfigVersionResponse version2 = countryConfigurationEngineService.upsertCountryConfiguration(
                "BJ",
                CountryConfigType.RISK_CONFIG,
                new UpsertCountryConfigRequest(secondPayload, OffsetDateTime.now().plusDays(2)),
                UUID.randomUUID()
        );
        assertEquals(2, version2.version());

        CountryConfigSnapshotResponse snapshotNow = countryConfigurationEngineService
                .getCountryConfiguration("BJ", OffsetDateTime.now());
        var riskNow = snapshotNow.configs().stream()
                .filter(item -> item.configType() == CountryConfigType.RISK_CONFIG)
                .findFirst()
                .orElseThrow();
        assertEquals(1, riskNow.version());
        assertEquals(1000, riskNow.payload().get("maxLoanAmount").asInt());

        CountryConfigSnapshotResponse snapshotFuture = countryConfigurationEngineService
                .getCountryConfiguration("BJ", OffsetDateTime.now().plusDays(3));
        var riskFuture = snapshotFuture.configs().stream()
                .filter(item -> item.configType() == CountryConfigType.RISK_CONFIG)
                .findFirst()
                .orElseThrow();
        assertEquals(2, riskFuture.version());
        assertEquals(2500, riskFuture.payload().get("maxLoanAmount").asInt());

        assertTrue(countryConfigurationEngineService.hasEffectiveConfiguration("BJ", CountryConfigType.RISK_CONFIG));
    }

    @Test
    void upsertFeatureFlags_shouldRejectNonBooleanFlags() {
        countryRepository.save(Country.builder()
                .id(UUID.randomUUID())
                .code("CI")
                .name("Cote d'Ivoire")
                .currency("XOF")
                .active(true)
                .configured(true)
                .fallback(false)
                .build());

        ObjectNode invalidFeatureFlags = objectMapper.createObjectNode()
                .put("chat_enabled", true)
                .put("max_partners", 20);

        assertThrows(BusinessException.class, () -> countryConfigurationEngineService.upsertCountryConfiguration(
                "CI",
                CountryConfigType.FEATURE_FLAGS,
                new UpsertCountryConfigRequest(invalidFeatureFlags, OffsetDateTime.now()),
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
        assertTrue(updatedCountry.isConfigured());

        ObjectNode riskPayloadV2 = objectMapper.createObjectNode()
                .put("maxExposure", 100000)
                .put("maxDefaultRate", 0.2);
        CountryConfigVersionResponse riskV2 = countryConfigurationEngineService.upsertCountryConfiguration(
                "SN",
                CountryConfigType.RISK_CONFIG,
                new UpsertCountryConfigRequest(riskPayloadV2, OffsetDateTime.now().plusDays(2)),
                UUID.randomUUID()
        );

        ObjectNode riskPayloadV3 = objectMapper.createObjectNode()
                .put("maxExposure", 50000)
                .put("maxDefaultRate", 0.1);
        CountryConfigVersionResponse riskV3 = countryConfigurationEngineService.upsertCountryConfiguration(
                "SN",
                CountryConfigType.RISK_CONFIG,
                new UpsertCountryConfigRequest(riskPayloadV3, OffsetDateTime.now().plusDays(4)),
                UUID.randomUUID()
        );
        assertTrue(riskV3.version() > riskV2.version());

        CountryConfigVersionResponse activated = countryConfigurationEngineService.activateCountryConfigurationVersion(
                "SN",
                CountryConfigType.RISK_CONFIG,
                new ActivateCountryConfigVersionRequest(riskV2.version(), OffsetDateTime.now()),
                UUID.randomUUID()
        );
        assertEquals(riskPayloadV2.get("maxExposure").asInt(), activated.payload().get("maxExposure").asInt());

        List<CountryConfigVersionHistoryItemResponse> versions = countryConfigurationEngineService
                .listCountryConfigurationVersions("SN", CountryConfigType.RISK_CONFIG);
        assertTrue(versions.size() >= 4);
        assertTrue(versions.stream().anyMatch(CountryConfigVersionHistoryItemResponse::active));
    }

    @Test
    void fullConfig_shouldReturnAggregatedPayloadsByDomain() {
        countryRepository.save(Country.builder()
                .id(UUID.randomUUID())
                .code("TG")
                .name("Togo")
                .currency("XOF")
                .active(true)
                .configured(true)
                .fallback(false)
                .build());

        ObjectNode accountingPayload = objectMapper.createObjectNode();
        ObjectNode preallocation = objectMapper.createObjectNode();
        preallocation.put("loanDisbursement", "ACC-1100");
        preallocation.put("fees", "ACC-2100");
        accountingPayload.set("preallocationAccounts", preallocation);

        countryConfigurationEngineService.upsertCountryConfiguration(
                "TG",
                CountryConfigType.ACCOUNTING_CONFIG,
                new UpsertCountryConfigRequest(accountingPayload, OffsetDateTime.now()),
                UUID.randomUUID()
        );

        ObjectNode membershipPayload = objectMapper.createObjectNode();
        ArrayNode plans = objectMapper.createArrayNode();
        ObjectNode basicPlan = objectMapper.createObjectNode();
        basicPlan.put("name", "Basic");
        ObjectNode pricing = objectMapper.createObjectNode();
        pricing.put("amount", 2000);
        pricing.put("currency", "XOF");
        basicPlan.set("pricing", pricing);
        basicPlan.set("benefits", objectMapper.createArrayNode().add("Community chat"));
        plans.add(basicPlan);
        membershipPayload.set("plans", plans);

        countryConfigurationEngineService.upsertCountryConfiguration(
                "TG",
                CountryConfigType.MEMBERSHIP_CONFIG,
                new UpsertCountryConfigRequest(membershipPayload, OffsetDateTime.now()),
                UUID.randomUUID()
        );

        CountryConfigFullResponse full = countryConfigurationEngineService.getFullCountryConfiguration("TG", OffsetDateTime.now());
        assertEquals("ACC-1100", full.accounting().get("preallocationAccounts").get("loanDisbursement").asText());
        assertEquals("Basic", full.membership().get("plans").get(0).get("name").asText());
        assertTrue(full.features().isObject());
    }
}
