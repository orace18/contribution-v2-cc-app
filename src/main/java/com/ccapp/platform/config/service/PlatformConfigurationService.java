package com.ccapp.platform.config.service;

import com.ccapp.admin.service.ConfigAuditMetadataService;
import com.ccapp.common.dto.ConfigAuditMetadataResponse;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.platform.config.domain.entity.PlatformConfigVersion;
import com.ccapp.platform.config.domain.enums.PlatformConfigType;
import com.ccapp.platform.config.dto.PlatformConfigSnapshotItemResponse;
import com.ccapp.platform.config.dto.PlatformConfigSnapshotResponse;
import com.ccapp.platform.config.dto.PlatformConfigVersionResponse;
import com.ccapp.platform.config.dto.UpsertPlatformConfigRequest;
import com.ccapp.country.config.service.CountryConfigResolver;
import com.ccapp.platform.config.repository.PlatformConfigVersionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlatformConfigurationService {

    private static final int MAX_PAYLOAD_CHARS = 50_000;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final String PLATFORM_CONFIG_ENTITY_TYPE = "PLATFORM_CONFIG";

    private final PlatformConfigVersionRepository platformConfigVersionRepository;
    private final ConfigAuditMetadataService configAuditMetadataService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PlatformConfigSnapshotResponse getPlatformConfiguration(OffsetDateTime asOf) {
        OffsetDateTime asOfDate = asOf == null ? OffsetDateTime.now() : asOf;
        var configs = Arrays.stream(PlatformConfigType.values())
                .map(configType -> platformConfigVersionRepository
                        .findFirstByConfigTypeAndEffectiveDateLessThanEqualOrderByEffectiveDateDescVersionDesc(configType, asOfDate)
                        .map(this::toSnapshotItem)
                        .orElse(new PlatformConfigSnapshotItemResponse(
                                configType,
                                null,
                                null,
                                null,
                                emptyAudit("DRAFT")
                        ))
                )
                .toList();
        return new PlatformConfigSnapshotResponse(asOfDate, configs);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(
                    cacheNames = CountryConfigResolver.PLATFORM_CONFIG_CACHE,
                    key = "'platform_config:' + #configType.externalName()"
            ),
            @CacheEvict(cacheNames = CountryConfigResolver.COUNTRY_CONFIG_CACHE, allEntries = true)
    })
    public PlatformConfigVersionResponse upsertPlatformConfiguration(PlatformConfigType configType,
                                                                    UpsertPlatformConfigRequest request,
                                                                    UUID actorAdminUserId) {
        JsonNode sanitizedPayload = validatePayload(configType, request.payload());

        int nextVersion = platformConfigVersionRepository.findFirstByConfigTypeOrderByVersionDesc(configType)
                .map(existing -> existing.getVersion() + 1)
                .orElse(1);

        PlatformConfigVersion saved = platformConfigVersionRepository.save(PlatformConfigVersion.builder()
                .configType(configType)
                .version(nextVersion)
                .effectiveDate(request.effectiveDate() == null ? OffsetDateTime.now() : request.effectiveDate())
                .payload(writePayload(sanitizedPayload))
                .createdByAdminUserId(actorAdminUserId)
                .build());

        return toVersionResponse(saved);
    }

    private PlatformConfigSnapshotItemResponse toSnapshotItem(PlatformConfigVersion entity) {
        ConfigAuditMetadataResponse audit = configAuditMetadataService.build(
                PLATFORM_CONFIG_ENTITY_TYPE,
                entity.getId(),
                entity.getVersion(),
                "ACTIVE",
                entity.getEffectiveDate(),
                entity.getCreatedAt(),
                entity.getCreatedByAdminUserId()
        );
        return new PlatformConfigSnapshotItemResponse(
                entity.getConfigType(),
                entity.getVersion(),
                entity.getEffectiveDate(),
                readPayload(entity.getPayload()),
                audit
        );
    }

    private PlatformConfigVersionResponse toVersionResponse(PlatformConfigVersion entity) {
        ConfigAuditMetadataResponse audit = configAuditMetadataService.build(
                PLATFORM_CONFIG_ENTITY_TYPE,
                entity.getId(),
                entity.getVersion(),
                resolveStatus(entity),
                entity.getEffectiveDate(),
                entity.getCreatedAt(),
                entity.getCreatedByAdminUserId()
        );
        return new PlatformConfigVersionResponse(
                entity.getId(),
                entity.getConfigType(),
                entity.getVersion(),
                entity.getEffectiveDate(),
                readPayload(entity.getPayload()),
                entity.getCreatedByAdminUserId(),
                entity.getCreatedAt(),
                audit
        );
    }

    private String resolveStatus(PlatformConfigVersion entity) {
        Integer activeVersion = platformConfigVersionRepository
                .findFirstByConfigTypeAndEffectiveDateLessThanEqualOrderByEffectiveDateDescVersionDesc(
                        entity.getConfigType(),
                        OffsetDateTime.now()
                )
                .map(PlatformConfigVersion::getVersion)
                .orElse(null);
        if (activeVersion != null && activeVersion.equals(entity.getVersion())) {
            return "ACTIVE";
        }
        return entity.getEffectiveDate() != null && entity.getEffectiveDate().isAfter(OffsetDateTime.now())
                ? "SCHEDULED"
                : "INACTIVE";
    }

    private ConfigAuditMetadataResponse emptyAudit(String status) {
        return new ConfigAuditMetadataResponse(
                null,
                status,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private JsonNode validatePayload(PlatformConfigType configType, JsonNode payload) {
        if (payload == null || payload.isNull()) {
            throw new BusinessException("Configuration payload is required");
        }
        if (!payload.isObject()) {
            throw new BusinessException("Configuration payload must be a JSON object");
        }
        if (payload.size() == 0) {
            throw new BusinessException("Configuration payload cannot be empty");
        }

        String rawPayload = writePayload(payload);
        if (rawPayload.length() > MAX_PAYLOAD_CHARS) {
            throw new BusinessException("Configuration payload is too large");
        }

        ObjectNode objectPayload = (ObjectNode) payload;
        switch (configType) {
            case GLOBAL_GENERAL -> validateGlobalGeneralConfig(objectPayload);
            case GLOBAL_MLM_BASE_RATES -> validateGlobalMlmBaseRates(objectPayload);
            case GLOBAL_ALLOCATION -> validateGlobalAllocationConfig(objectPayload);
        }

        return objectPayload;
    }

    private void validateGlobalGeneralConfig(ObjectNode payload) {
        optionalPercentageField(payload, "marketplaceDefaultCommission", "global_general.marketplaceDefaultCommission");
        optionalPercentageField(payload, "taxRate", "global_general.taxRate");
    }

    private void validateGlobalMlmBaseRates(ObjectNode payload) {
        requiredPercentageField(payload, "unilevel", "global_mlm_base_rates.unilevel");
        requiredPercentageField(payload, "binary", "global_mlm_base_rates.binary");
    }

    private void validateGlobalAllocationConfig(ObjectNode payload) {
        JsonNode percentagesNode = payload.has("percentages") ? payload.get("percentages") : payload;
        if (!percentagesNode.isObject()) {
            throw new BusinessException("global_allocation.percentages must be an object");
        }
        ObjectNode percentages = (ObjectNode) percentagesNode;
        if (percentages.isEmpty()) {
            throw new BusinessException("global_allocation.percentages must not be empty");
        }

        BigDecimal total = BigDecimal.ZERO;
        for (var fields = percentages.fields(); fields.hasNext(); ) {
            var field = fields.next();
            JsonNode value = field.getValue();
            if (!value.isNumber()) {
                throw new BusinessException("global_allocation percentages must be numeric");
            }
            BigDecimal decimal = value.decimalValue();
            if (decimal.compareTo(BigDecimal.ZERO) < 0 || decimal.compareTo(ONE_HUNDRED) > 0) {
                throw new BusinessException("global_allocation percentages must be between 0 and 100");
            }
            total = total.add(decimal);
        }

        if (total.setScale(4, RoundingMode.HALF_UP).compareTo(ONE_HUNDRED.setScale(4, RoundingMode.HALF_UP)) != 0) {
            throw new BusinessException("allocation percentages must sum to 100%");
        }
    }

    private BigDecimal optionalPercentageField(ObjectNode payload, String fieldName, String errorPrefix) {
        JsonNode value = payload.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isNumber()) {
            throw new BusinessException(errorPrefix + " must be numeric");
        }
        BigDecimal decimal = value.decimalValue();
        if (decimal.compareTo(BigDecimal.ZERO) < 0 || decimal.compareTo(ONE_HUNDRED) > 0) {
            throw new BusinessException(errorPrefix + " must be between 0 and 100");
        }
        return decimal;
    }

    private BigDecimal requiredPercentageField(ObjectNode payload, String fieldName, String errorPrefix) {
        JsonNode value = payload.get(fieldName);
        if (value == null || value.isNull() || !value.isNumber()) {
            throw new BusinessException(errorPrefix + " is required and must be numeric");
        }
        BigDecimal decimal = value.decimalValue();
        if (decimal.compareTo(BigDecimal.ZERO) < 0 || decimal.compareTo(ONE_HUNDRED) > 0) {
            throw new BusinessException(errorPrefix + " must be between 0 and 100");
        }
        return decimal;
    }

    private String writePayload(JsonNode payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Invalid configuration payload");
        }
    }

    private JsonNode readPayload(String rawPayload) {
        try {
            return objectMapper.readTree(rawPayload);
        } catch (JsonProcessingException ex) {
            ObjectNode fallback = objectMapper.createObjectNode();
            fallback.put("legacyRaw", rawPayload);
            return fallback;
        }
    }
}
