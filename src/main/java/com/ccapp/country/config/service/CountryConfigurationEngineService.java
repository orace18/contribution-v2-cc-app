package com.ccapp.country.config.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.domain.entity.CountryConfigVersion;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.dto.CountryConfigSnapshotItemResponse;
import com.ccapp.country.config.dto.CountryConfigSnapshotResponse;
import com.ccapp.country.config.dto.CountryConfigVersionResponse;
import com.ccapp.country.config.dto.UpsertCountryConfigRequest;
import com.ccapp.country.config.repository.CountryConfigVersionRepository;
import com.ccapp.country.repository.CountryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CountryConfigurationEngineService {

    private static final int MAX_PAYLOAD_CHARS = 50_000;

    private final CountryConfigVersionRepository countryConfigVersionRepository;
    private final CountryRepository countryRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public CountryConfigSnapshotResponse getCountryConfiguration(String countryCode, OffsetDateTime asOf) {
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        assertCountryExists(normalizedCountryCode);

        OffsetDateTime asOfDate = asOf == null ? OffsetDateTime.now() : asOf;
        var configs = Arrays.stream(CountryConfigType.values())
                .map(configType -> countryConfigVersionRepository
                        .findFirstByCountryCodeAndConfigTypeAndEffectiveDateLessThanEqualOrderByEffectiveDateDescVersionDesc(
                                normalizedCountryCode,
                                configType,
                                asOfDate
                        )
                        .map(this::toSnapshotItem)
                        .orElse(new CountryConfigSnapshotItemResponse(configType, null, null, null))
                )
                .toList();

        return new CountryConfigSnapshotResponse(normalizedCountryCode, asOfDate, configs);
    }

    @Transactional
    public CountryConfigVersionResponse upsertCountryConfiguration(String countryCode,
                                                                  CountryConfigType configType,
                                                                  UpsertCountryConfigRequest request,
                                                                  UUID actorAdminUserId) {
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        assertCountryExists(normalizedCountryCode);
        JsonNode sanitizedPayload = validatePayload(configType, request.payload());

        int nextVersion = countryConfigVersionRepository.findFirstByCountryCodeAndConfigTypeOrderByVersionDesc(
                        normalizedCountryCode,
                        configType
                )
                .map(existing -> existing.getVersion() + 1)
                .orElse(1);

        CountryConfigVersion saved = countryConfigVersionRepository.save(CountryConfigVersion.builder()
                .countryCode(normalizedCountryCode)
                .configType(configType)
                .version(nextVersion)
                .effectiveDate(request.effectiveDate() == null ? OffsetDateTime.now() : request.effectiveDate())
                .payload(writePayload(sanitizedPayload))
                .createdByAdminUserId(actorAdminUserId)
                .build());

        return toVersionResponse(saved);
    }

    @Transactional(readOnly = true)
    public boolean hasEffectiveConfiguration(String countryCode, CountryConfigType configType) {
        if (countryCode == null || countryCode.isBlank()) {
            return false;
        }

        return countryConfigVersionRepository
                .findFirstByCountryCodeAndConfigTypeAndEffectiveDateLessThanEqualOrderByEffectiveDateDescVersionDesc(
                        countryCode.trim().toUpperCase(Locale.ROOT),
                        configType,
                        OffsetDateTime.now()
                )
                .isPresent();
    }

    private CountryConfigSnapshotItemResponse toSnapshotItem(CountryConfigVersion entity) {
        return new CountryConfigSnapshotItemResponse(
                entity.getConfigType(),
                entity.getVersion(),
                entity.getEffectiveDate(),
                readPayload(entity.getPayload())
        );
    }

    private CountryConfigVersionResponse toVersionResponse(CountryConfigVersion entity) {
        return new CountryConfigVersionResponse(
                entity.getId(),
                entity.getCountryCode(),
                entity.getConfigType(),
                entity.getVersion(),
                entity.getEffectiveDate(),
                readPayload(entity.getPayload()),
                entity.getCreatedByAdminUserId(),
                entity.getCreatedAt()
        );
    }

    private JsonNode validatePayload(CountryConfigType configType, JsonNode payload) {
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

        if (configType == CountryConfigType.FEATURE_FLAGS) {
            payload.properties().forEach(entry -> {
                if (!entry.getValue().isBoolean()) {
                    throw new BusinessException("feature_flags values must be boolean");
                }
            });
        }

        return payload;
    }

    private String normalizeCountryCode(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            throw new BusinessException("Country code is required");
        }
        String normalized = countryCode.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() < 2 || normalized.length() > 3) {
            throw new BusinessException("Country code must be 2-3 characters");
        }
        return normalized;
    }

    private void assertCountryExists(String countryCode) {
        if (countryRepository.findByCode(countryCode).isEmpty()) {
            throw new BusinessException("Country not found");
        }
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
