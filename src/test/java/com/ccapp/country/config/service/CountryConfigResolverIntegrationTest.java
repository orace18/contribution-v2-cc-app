package com.ccapp.country.config.service;

import com.ccapp.config.TestJacksonAndCachingConfig;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.dto.UpsertCountryConfigRequest;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.repository.CountryRepository;
import com.ccapp.platform.config.domain.enums.PlatformConfigType;
import com.ccapp.platform.config.dto.UpsertPlatformConfigRequest;
import com.ccapp.platform.config.service.PlatformConfigurationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
        CountryConfigurationEngineService.class,
        PlatformConfigurationService.class,
        CountryConfigResolver.class,
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
        ObjectNode platformMlm = objectMapper.createObjectNode();
        platformMlm.put("generations", 4);
        ObjectNode caps = objectMapper.createObjectNode();
        caps.put("monthly", 5000);
        platformMlm.set("caps", caps);
        platformConfigurationService.upsertPlatformConfiguration(
                PlatformConfigType.GLOBAL_MLM_FALLBACK,
                new UpsertPlatformConfigRequest(platformMlm, OffsetDateTime.now()),
                UUID.randomUUID()
        );

        CountryConfigResolver.ResolvedConfig resolved = countryConfigResolver.resolveActiveConfig("BJ", CountryConfigType.MLM_CONFIG);
        ObjectNode resolvedPayload = countryConfigResolver.payloadAsObjectNode(resolved);

        assertEquals(CountryConfigResolver.ConfigSource.PLATFORM, resolved.source());
        assertEquals(4, resolvedPayload.get("generations").asInt());
    }

    @Test
    void resolveActiveConfig_shouldInvalidateCacheOnCountryConfigUpdate() {
        ObjectNode v1 = objectMapper.createObjectNode().put("maxMembers", 4);
        countryConfigurationEngineService.upsertCountryConfiguration(
                "BJ",
                CountryConfigType.CIRCLE_CONFIG,
                new UpsertCountryConfigRequest(v1, OffsetDateTime.now()),
                UUID.randomUUID()
        );
        CountryConfigResolver.ResolvedConfig first = countryConfigResolver.resolveActiveConfig("BJ", CountryConfigType.CIRCLE_CONFIG);
        assertEquals(4, countryConfigResolver.payloadAsObjectNode(first).get("maxMembers").asInt());

        ObjectNode v2 = objectMapper.createObjectNode().put("maxMembers", 3);
        countryConfigurationEngineService.upsertCountryConfiguration(
                "BJ",
                CountryConfigType.CIRCLE_CONFIG,
                new UpsertCountryConfigRequest(v2, OffsetDateTime.now()),
                UUID.randomUUID()
        );
        CountryConfigResolver.ResolvedConfig refreshed = countryConfigResolver.resolveActiveConfig("BJ", CountryConfigType.CIRCLE_CONFIG);
        assertEquals(3, countryConfigResolver.payloadAsObjectNode(refreshed).get("maxMembers").asInt());
    }

    @Test
    void resolveActiveConfig_shouldPreferCountryOverPlatform() {
        ObjectNode membershipPayload = objectMapper.createObjectNode();
        ArrayNode plans = objectMapper.createArrayNode();
        ObjectNode plan = objectMapper.createObjectNode();
        plan.put("name", "Basic");
        ObjectNode pricing = objectMapper.createObjectNode();
        pricing.put("amount", 1000);
        pricing.put("currency", "XOF");
        plan.set("pricing", pricing);
        plan.set("benefits", objectMapper.createArrayNode().add("Community"));
        plans.add(plan);
        membershipPayload.set("plans", plans);
        countryConfigurationEngineService.upsertCountryConfiguration(
                "BJ",
                CountryConfigType.MEMBERSHIP_CONFIG,
                new UpsertCountryConfigRequest(membershipPayload, OffsetDateTime.now()),
                UUID.randomUUID()
        );

        ObjectNode platformMlm = objectMapper.createObjectNode();
        platformMlm.put("generations", 4);
        ObjectNode platformCaps = objectMapper.createObjectNode();
        platformCaps.put("monthly", 9000);
        platformMlm.set("caps", platformCaps);
        platformConfigurationService.upsertPlatformConfiguration(
                PlatformConfigType.GLOBAL_MLM_FALLBACK,
                new UpsertPlatformConfigRequest(platformMlm, OffsetDateTime.now()),
                UUID.randomUUID()
        );

        ObjectNode countryMlm = objectMapper.createObjectNode();
        countryMlm.put("generations", 2);
        ArrayNode rates = objectMapper.createArrayNode();
        rates.add(objectMapper.createObjectNode().put("generation", 1).put("rate", 0.1));
        rates.add(objectMapper.createObjectNode().put("generation", 2).put("rate", 0.05));
        countryMlm.set("commissionRates", rates);
        ObjectNode countryCaps = objectMapper.createObjectNode();
        countryCaps.put("daily", 100);
        countryCaps.put("monthly", 1000);
        countryMlm.set("caps", countryCaps);
        countryConfigurationEngineService.upsertCountryConfiguration(
                "BJ",
                CountryConfigType.MLM_CONFIG,
                new UpsertCountryConfigRequest(countryMlm, OffsetDateTime.now()),
                UUID.randomUUID()
        );

        CountryConfigResolver.ResolvedConfig resolved = countryConfigResolver.resolveActiveConfig("BJ", CountryConfigType.MLM_CONFIG);
        ObjectNode payload = countryConfigResolver.payloadAsObjectNode(resolved);

        assertEquals(CountryConfigResolver.ConfigSource.COUNTRY, resolved.source());
        assertEquals(2, payload.get("generations").asInt());
    }
}
