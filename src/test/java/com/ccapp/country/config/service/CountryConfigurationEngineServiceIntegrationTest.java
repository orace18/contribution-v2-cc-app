package com.ccapp.country.config.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.dto.CountryConfigSnapshotResponse;
import com.ccapp.country.config.dto.CountryConfigVersionResponse;
import com.ccapp.country.config.dto.UpsertCountryConfigRequest;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.repository.CountryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(CountryConfigurationEngineService.class)
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
}
