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
    void upsertAndSnapshot_shouldVersionGlobalGeneralConfig() {
        ObjectNode firstPayload = objectMapper.createObjectNode()
                .put("marketplaceDefaultCommission", 5)
                .put("taxRate", 18);

        PlatformConfigVersionResponse version1 = platformConfigurationService.upsertPlatformConfiguration(
                PlatformConfigType.GLOBAL_GENERAL,
                new UpsertPlatformConfigRequest(firstPayload, OffsetDateTime.now().minusHours(1)),
                UUID.randomUUID()
        );
        assertEquals(1, version1.version());

        ObjectNode secondPayload = objectMapper.createObjectNode()
                .put("marketplaceDefaultCommission", 7)
                .put("taxRate", 20);

        PlatformConfigVersionResponse version2 = platformConfigurationService.upsertPlatformConfiguration(
                PlatformConfigType.GLOBAL_GENERAL,
                new UpsertPlatformConfigRequest(secondPayload, OffsetDateTime.now().plusHours(2)),
                UUID.randomUUID()
        );
        assertEquals(2, version2.version());

        PlatformConfigSnapshotResponse nowSnapshot = platformConfigurationService.getPlatformConfiguration(OffsetDateTime.now());
        var globalGeneralNow = nowSnapshot.configs().stream()
                .filter(item -> item.configType() == PlatformConfigType.GLOBAL_GENERAL)
                .findFirst()
                .orElseThrow();
        assertEquals(1, globalGeneralNow.version());

        PlatformConfigSnapshotResponse futureSnapshot = platformConfigurationService.getPlatformConfiguration(OffsetDateTime.now().plusHours(4));
        var globalGeneralFuture = futureSnapshot.configs().stream()
                .filter(item -> item.configType() == PlatformConfigType.GLOBAL_GENERAL)
                .findFirst()
                .orElseThrow();
        assertEquals(2, globalGeneralFuture.version());
        assertEquals(7, globalGeneralFuture.payload().get("marketplaceDefaultCommission").asInt());
    }

    @Test
    void upsertGlobalMlmBaseRates_shouldRejectRateAbove100() {
        ObjectNode invalidRates = objectMapper.createObjectNode()
                .put("unilevel", 120)
                .put("binary", 15);

        assertThrows(BusinessException.class, () -> platformConfigurationService.upsertPlatformConfiguration(
                PlatformConfigType.GLOBAL_MLM_BASE_RATES,
                new UpsertPlatformConfigRequest(invalidRates, OffsetDateTime.now()),
                UUID.randomUUID()
        ));
    }

    @Test
    void upsertGlobalAllocation_shouldRejectWhenPercentagesDoNotSumTo100() {
        ObjectNode invalidAllocation = objectMapper.createObjectNode();
        ObjectNode percentages = objectMapper.createObjectNode();
        percentages.put("operations", 30);
        percentages.put("reserves", 30);
        percentages.put("mlm", 30);
        invalidAllocation.set("percentages", percentages);

        BusinessException exception = assertThrows(BusinessException.class, () -> platformConfigurationService.upsertPlatformConfiguration(
                PlatformConfigType.GLOBAL_ALLOCATION,
                new UpsertPlatformConfigRequest(invalidAllocation, OffsetDateTime.now()),
                UUID.randomUUID()
        ));
        assertTrue(exception.getMessage().contains("sum to 100"));
    }
}
