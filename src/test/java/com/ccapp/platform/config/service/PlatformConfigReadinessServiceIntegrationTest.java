package com.ccapp.platform.config.service;

import com.ccapp.admin.service.ConfigAuditMetadataService;
import com.ccapp.config.TestJacksonAndCachingConfig;
import com.ccapp.platform.config.domain.enums.PlatformConfigType;
import com.ccapp.platform.config.dto.PlatformConfigReadinessResponse;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        PlatformConfigurationService.class,
        PlatformConfigReadinessService.class,
        ConfigAuditMetadataService.class,
        TestJacksonAndCachingConfig.class
})
class PlatformConfigReadinessServiceIntegrationTest {

    @Autowired
    private PlatformConfigurationService platformConfigurationService;
    @Autowired
    private PlatformConfigReadinessService platformConfigReadinessService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void evaluateReadiness_shouldReturnReadyWhenAllChecksPass() {
        ObjectNode general = objectMapper.createObjectNode()
                .put("taxRate", 18)
                .put("marketplaceDefaultCommission", 5);
        ObjectNode featureFlags = objectMapper.createObjectNode()
                .put("chatEnabled", true)
                .put("marketplaceEnabled", true);
        ObjectNode systemLimits = objectMapper.createObjectNode()
                .put("minMembers", 5)
                .put("maxMembers", 50);
        general.set("featureFlags", featureFlags);
        general.set("systemLimits", systemLimits);

        ObjectNode mlm = objectMapper.createObjectNode()
                .put("unilevel", 12)
                .put("binary", 8);

        ObjectNode allocation = objectMapper.createObjectNode();
        ObjectNode percentages = objectMapper.createObjectNode()
                .put("operations", 30)
                .put("reserves", 25)
                .put("mlm", 25)
                .put("treasury", 20);
        allocation.set("percentages", percentages);

        platformConfigurationService.upsertPlatformConfiguration(
                PlatformConfigType.GLOBAL_GENERAL,
                new UpsertPlatformConfigRequest(general, OffsetDateTime.now()),
                UUID.randomUUID()
        );
        platformConfigurationService.upsertPlatformConfiguration(
                PlatformConfigType.GLOBAL_MLM_BASE_RATES,
                new UpsertPlatformConfigRequest(mlm, OffsetDateTime.now()),
                UUID.randomUUID()
        );
        platformConfigurationService.upsertPlatformConfiguration(
                PlatformConfigType.GLOBAL_ALLOCATION,
                new UpsertPlatformConfigRequest(allocation, OffsetDateTime.now()),
                UUID.randomUUID()
        );

        PlatformConfigReadinessResponse readiness = platformConfigReadinessService.evaluateReadiness(OffsetDateTime.now().plusMinutes(1));
        assertTrue(readiness.ready());
        assertEquals(100, readiness.score());
        assertTrue(readiness.missing().isEmpty());
    }

    @Test
    void evaluateReadiness_shouldExposeMissingChecksWhenConfigIncomplete() {
        ObjectNode general = objectMapper.createObjectNode()
                .put("taxRate", 18)
                .put("marketplaceDefaultCommission", 5);
        platformConfigurationService.upsertPlatformConfiguration(
                PlatformConfigType.GLOBAL_GENERAL,
                new UpsertPlatformConfigRequest(general, OffsetDateTime.now()),
                UUID.randomUUID()
        );

        PlatformConfigReadinessResponse readiness = platformConfigReadinessService.evaluateReadiness(OffsetDateTime.now().plusMinutes(1));
        assertFalse(readiness.ready());
        assertTrue(readiness.score() < 100);
        assertTrue(readiness.missing().contains("platform.global_mlm_config"));
        assertTrue(readiness.missing().contains("platform.allocation"));
        assertTrue(readiness.missing().contains("platform.feature_flags"));
        assertTrue(readiness.missing().contains("platform.system_limits"));
    }
}
