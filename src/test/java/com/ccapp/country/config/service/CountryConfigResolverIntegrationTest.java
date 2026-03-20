package com.ccapp.country.config.service;

import com.ccapp.admin.service.ConfigAuditMetadataService;
import com.ccapp.config.TestJacksonAndCachingConfig;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.dto.UpsertCountryConfigRequest;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.repository.CountryRepository;
import com.ccapp.platform.config.domain.enums.PlatformConfigType;
import com.ccapp.platform.config.dto.UpsertPlatformConfigRequest;
import com.ccapp.platform.config.service.PlatformConfigurationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        CountryChartOfAccountsService.class,
        CountryConfigurationEngineService.class,
        CountryConfigReadinessService.class,
        PlatformConfigurationService.class,
        CountryConfigResolver.class,
        ConfigAuditMetadataService.class,
        TestJacksonAndCachingConfig.class
})
class CountryConfigResolverIntegrationTest {

    @Autowired
    private CountryRepository countryRepository;
    @Autowired
    private CountryConfigurationEngineService countryConfigurationEngineService;
    @Autowired
    private PlatformConfigurationService platformConfigurationService;
    @Autowired
    private CountryConfigResolver countryConfigResolver;

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
    }

    @Test
    void resolveActiveConfig_shouldFallbackToPlatformWhenCountryIsMissing() {
        ObjectNode platformGeneral = objectMapper.createObjectNode();
        platformGeneral.put("taxRate", 18);
        platformConfigurationService.upsertPlatformConfiguration(
                PlatformConfigType.GLOBAL_GENERAL,
                new UpsertPlatformConfigRequest(platformGeneral, OffsetDateTime.now()),
                UUID.randomUUID()
        );

        CountryConfigResolver.ResolvedConfig resolved = countryConfigResolver.resolveActiveConfig("BJ", CountryConfigType.GENERAL);
        ObjectNode resolvedPayload = countryConfigResolver.payloadAsObjectNode(resolved);

        assertEquals(CountryConfigResolver.ConfigSource.PLATFORM, resolved.source());
        assertEquals(18, resolvedPayload.get("taxRate").asInt());
    }

    @Test
    void resolveActiveConfig_shouldInvalidateCacheOnCountryConfigUpdate() {
        ObjectNode v1 = objectMapper.createObjectNode().put("maxMembers", 4);
        countryConfigurationEngineService.upsertCountryConfiguration(
                "BJ",
                CountryConfigType.GENERAL,
                new UpsertCountryConfigRequest(v1, OffsetDateTime.now()),
                UUID.randomUUID()
        );
        CountryConfigResolver.ResolvedConfig first = countryConfigResolver.resolveActiveConfig("BJ", CountryConfigType.GENERAL);
        assertEquals(4, countryConfigResolver.payloadAsObjectNode(first).get("maxMembers").asInt());

        ObjectNode v2 = objectMapper.createObjectNode().put("maxMembers", 3);
        countryConfigurationEngineService.upsertCountryConfiguration(
                "BJ",
                CountryConfigType.GENERAL,
                new UpsertCountryConfigRequest(v2, OffsetDateTime.now()),
                UUID.randomUUID()
        );
        CountryConfigResolver.ResolvedConfig refreshed = countryConfigResolver.resolveActiveConfig("BJ", CountryConfigType.GENERAL);
        assertEquals(3, countryConfigResolver.payloadAsObjectNode(refreshed).get("maxMembers").asInt());
    }

    @Test
    void resolveActiveConfig_shouldMergeCountryOverrideOverPlatformDefaults() {
        ObjectNode platformRates = objectMapper.createObjectNode();
        platformRates.put("unilevel", 12);
        platformRates.put("binary", 8);
        platformConfigurationService.upsertPlatformConfiguration(
                PlatformConfigType.GLOBAL_MLM_BASE_RATES,
                new UpsertPlatformConfigRequest(platformRates, OffsetDateTime.now()),
                UUID.randomUUID()
        );

        ObjectNode countryMlmLimits = objectMapper.createObjectNode();
        countryMlmLimits.put("weeklyCap", 1000);
        countryConfigurationEngineService.upsertCountryConfiguration(
                "BJ",
                CountryConfigType.MLM_LIMITS,
                new UpsertCountryConfigRequest(countryMlmLimits, OffsetDateTime.now()),
                UUID.randomUUID()
        );

        CountryConfigResolver.ResolvedConfig resolved = countryConfigResolver.resolveActiveConfig("BJ", CountryConfigType.MLM_LIMITS);
        ObjectNode payload = countryConfigResolver.payloadAsObjectNode(resolved);

        assertEquals(CountryConfigResolver.ConfigSource.MERGED, resolved.source());
        assertEquals(12, payload.get("unilevel").asInt());
        assertEquals(8, payload.get("binary").asInt());
        assertEquals(1000, payload.get("weeklyCap").asInt());
    }

    @Test
    void resolveActiveConfig_shouldKeepPlatformFallbackWhenCountryGeneralHasPartialFields() {
        ObjectNode platformGeneral = objectMapper.createObjectNode();
        platformGeneral.put("taxRate", 18);
        platformGeneral.put("marketplaceDefaultCommission", 5);
        platformConfigurationService.upsertPlatformConfiguration(
                PlatformConfigType.GLOBAL_GENERAL,
                new UpsertPlatformConfigRequest(platformGeneral, OffsetDateTime.now()),
                UUID.randomUUID()
        );

        ObjectNode countryGeneralOverride = objectMapper.createObjectNode();
        countryGeneralOverride.put("taxRate", 20);
        countryConfigurationEngineService.upsertCountryConfiguration(
                "BJ",
                CountryConfigType.GENERAL,
                new UpsertCountryConfigRequest(countryGeneralOverride, OffsetDateTime.now()),
                UUID.randomUUID()
        );

        CountryConfigResolver.ResolvedConfig resolved = countryConfigResolver.resolveActiveConfig("BJ", CountryConfigType.GENERAL);
        ObjectNode payload = countryConfigResolver.payloadAsObjectNode(resolved);

        assertEquals(CountryConfigResolver.ConfigSource.MERGED, resolved.source());
        assertEquals(20, payload.get("taxRate").asInt());
        assertEquals(5, payload.get("marketplaceDefaultCommission").asInt());
    }
}
