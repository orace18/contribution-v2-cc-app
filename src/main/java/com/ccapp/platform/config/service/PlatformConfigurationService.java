package com.ccapp.platform.config.service;

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
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlatformConfigurationService {

    private static final int MAX_PAYLOAD_CHARS = 50_000;

    private final PlatformConfigVersionRepository platformConfigVersionRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PlatformConfigSnapshotResponse getPlatformConfiguration(OffsetDateTime asOf) {
        OffsetDateTime asOfDate = asOf == null ? OffsetDateTime.now() : asOf;
        var configs = Arrays.stream(PlatformConfigType.values())
                .map(configType -> platformConfigVersionRepository
                        .findFirstByConfigTypeAndEffectiveDateLessThanEqualOrderByEffectiveDateDescVersionDesc(configType, asOfDate)
                        .map(this::toSnapshotItem)
                        .orElse(new PlatformConfigSnapshotItemResponse(configType, null, null, null))
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
        return new PlatformConfigSnapshotItemResponse(
                entity.getConfigType(),
                entity.getVersion(),
                entity.getEffectiveDate(),
                readPayload(entity.getPayload())
        );
    }

    private PlatformConfigVersionResponse toVersionResponse(PlatformConfigVersion entity) {
        return new PlatformConfigVersionResponse(
                entity.getId(),
                entity.getConfigType(),
                entity.getVersion(),
                entity.getEffectiveDate(),
                readPayload(entity.getPayload()),
                entity.getCreatedByAdminUserId(),
                entity.getCreatedAt()
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
            case GLOBAL_FEATURE_FLAGS -> objectPayload.properties().forEach(entry -> {
                if (!entry.getValue().isBoolean()) {
                    throw new BusinessException("global_feature_flags values must be boolean");
                }
            });
            case SYSTEM_LIMITS -> objectPayload.properties().forEach(entry -> {
                JsonNode value = entry.getValue();
                if (!value.isNumber() || value.decimalValue().compareTo(BigDecimal.ZERO) < 0) {
                    throw new BusinessException("system_limits values must be non-negative numbers");
                }
            });
            case GLOBAL_MLM_FALLBACK -> {
                JsonNode generations = objectPayload.get("generations");
                if (generations == null || !generations.canConvertToInt() || generations.intValue() < 1) {
                    throw new BusinessException("global_mlm_fallback.generations must be >= 1");
                }
                JsonNode caps = objectPayload.get("caps");
                if (caps == null || !caps.isObject()) {
                    throw new BusinessException("global_mlm_fallback.caps must be an object");
                }
                ObjectNode capsNode = (ObjectNode) caps;
                JsonNode monthly = capsNode.get("monthly");
                if (monthly == null || !monthly.isNumber() || monthly.decimalValue().compareTo(BigDecimal.ZERO) < 0) {
                    throw new BusinessException("global_mlm_fallback.caps.monthly must be >= 0");
                }
            }
            default -> {
            }
        }

        return objectPayload;
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
