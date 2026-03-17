package com.ccapp.platform.config.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.config.TestJacksonAndCachingConfig;
import com.ccapp.platform.config.domain.enums.PlatformConfigType;
import com.ccapp.platform.config.dto.PlatformConfigSnapshotResponse;
import com.ccapp.platform.config.dto.PlatformConfigVersionResponse;
import com.ccapp.platform.config.dto.UpsertPlatformConfigRequest;
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
@Import({PlatformConfigurationService.class, TestJacksonAndCachingConfig.class})
class PlatformConfigurationServiceIntegrationTest {

    @Autowired
    private PlatformConfigurationService platformConfigurationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void upsertAndSnapshot_shouldVersionPlatformConfigs() {
        ObjectNode limitsPayload = objectMapper.createObjectNode()
                .put("maxPartnersPerCountry", 100)
                .put("maxDailyDisbursement", 1_000_000);

        PlatformConfigVersionResponse version1 = platformConfigurationService.upsertPlatformConfiguration(
                PlatformConfigType.SYSTEM_LIMITS,
                new UpsertPlatformConfigRequest(limitsPayload, OffsetDateTime.now().minusHours(1)),
                UUID.randomUUID()
        );
        assertEquals(1, version1.version());

        ObjectNode updatedLimitsPayload = objectMapper.createObjectNode()
                .put("maxPartnersPerCountry", 150)
                .put("maxDailyDisbursement", 2_000_000);

        PlatformConfigVersionResponse version2 = platformConfigurationService.upsertPlatformConfiguration(
                PlatformConfigType.SYSTEM_LIMITS,
                new UpsertPlatformConfigRequest(updatedLimitsPayload, OffsetDateTime.now().plusHours(2)),
                UUID.randomUUID()
        );
        assertEquals(2, version2.version());

        PlatformConfigSnapshotResponse nowSnapshot = platformConfigurationService.getPlatformConfiguration(OffsetDateTime.now());
        var systemLimitsNow = nowSnapshot.configs().stream()
                .filter(item -> item.configType() == PlatformConfigType.SYSTEM_LIMITS)
                .findFirst()
                .orElseThrow();
        assertEquals(1, systemLimitsNow.version());

        PlatformConfigSnapshotResponse futureSnapshot = platformConfigurationService.getPlatformConfiguration(OffsetDateTime.now().plusHours(4));
        var systemLimitsFuture = futureSnapshot.configs().stream()
                .filter(item -> item.configType() == PlatformConfigType.SYSTEM_LIMITS)
                .findFirst()
                .orElseThrow();
        assertEquals(2, systemLimitsFuture.version());
        assertEquals(150, systemLimitsFuture.payload().get("maxPartnersPerCountry").asInt());
    }

    @Test
    void upsertFeatureFlags_shouldRejectNonBooleanFlags() {
        ObjectNode invalidFeatureFlags = objectMapper.createObjectNode()
                .put("financingEnabled", true)
                .put("maxPartners", 120);

        assertThrows(BusinessException.class, () -> platformConfigurationService.upsertPlatformConfiguration(
                PlatformConfigType.GLOBAL_FEATURE_FLAGS,
                new UpsertPlatformConfigRequest(invalidFeatureFlags, OffsetDateTime.now()),
                UUID.randomUUID()
        ));
    }

    @Test
    void upsertSystemLimits_shouldRejectNegativeValues() {
        ObjectNode invalidLimits = objectMapper.createObjectNode()
                .put("maxDailyDisbursement", -1);

        BusinessException exception = assertThrows(BusinessException.class, () -> platformConfigurationService.upsertPlatformConfiguration(
                PlatformConfigType.SYSTEM_LIMITS,
                new UpsertPlatformConfigRequest(invalidLimits, OffsetDateTime.now()),
                UUID.randomUUID()
        ));
        assertTrue(exception.getMessage().contains("non-negative"));
    }
}
