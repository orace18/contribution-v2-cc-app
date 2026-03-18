package com.ccapp.country.config.service;

import com.ccapp.country.config.domain.entity.CountryConfigVersion;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.repository.CountryConfigVersionRepository;
import com.ccapp.platform.config.domain.entity.PlatformConfigVersion;
import com.ccapp.platform.config.domain.enums.PlatformConfigType;
import com.ccapp.platform.config.repository.PlatformConfigVersionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CountryConfigResolver {

    public static final String COUNTRY_CONFIG_CACHE = "countryConfigActive";
    public static final String PLATFORM_CONFIG_CACHE = "platformConfigActive";

    private static final String EMPTY_JSON_OBJECT = "{}";

    private final CountryConfigVersionRepository countryConfigVersionRepository;
    private final PlatformConfigVersionRepository platformConfigVersionRepository;
    private final ObjectMapper objectMapper;

    @Cacheable(
            cacheNames = COUNTRY_CONFIG_CACHE,
            key = "'country_config:' + #countryCode.trim().toUpperCase(T(java.util.Locale).ROOT) + ':' + #configType.externalName()"
    )
    @Transactional(readOnly = true)
    public ResolvedConfig resolveActiveConfig(String countryCode, CountryConfigType configType) {
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        OffsetDateTime now = OffsetDateTime.now();

        CountryConfigVersion countryConfig = countryConfigVersionRepository
                .findFirstByCountryCodeAndConfigTypeAndEffectiveDateLessThanEqualOrderByEffectiveDateDescVersionDesc(
                        normalizedCountryCode,
                        configType,
                        now
                )
                .orElse(null);

        ObjectNode mergedPayload = objectMapper.createObjectNode();
        Integer resolvedVersion = null;
        boolean hasPlatformDefaults = false;

        for (PlatformConfigType fallbackType : mapFallbackTypes(configType)) {
            ResolvedPlatformConfig platformConfig = resolveActivePlatformConfig(fallbackType);
            if (platformConfig.version() != null) {
                hasPlatformDefaults = true;
                deepMergeInto(mergedPayload, readObjectPayload(platformConfig.payload()));
                resolvedVersion = maxVersion(resolvedVersion, platformConfig.version());
            }
        }

        if (countryConfig != null) {
            deepMergeInto(mergedPayload, readObjectPayload(countryConfig.getPayload()));
            return new ResolvedConfig(
                    normalizedCountryCode,
                    configType,
                    countryConfig.getVersion(),
                    writePayload(mergedPayload),
                    hasPlatformDefaults ? ConfigSource.MERGED : ConfigSource.COUNTRY
            );
        }

        if (hasPlatformDefaults) {
            return new ResolvedConfig(
                    normalizedCountryCode,
                    configType,
                    resolvedVersion,
                    writePayload(mergedPayload),
                    ConfigSource.PLATFORM
            );
        }

        return new ResolvedConfig(normalizedCountryCode, configType, null, EMPTY_JSON_OBJECT, ConfigSource.EMPTY);
    }

    @Cacheable(
            cacheNames = PLATFORM_CONFIG_CACHE,
            key = "'platform_config:' + #configType.externalName()"
    )
    @Transactional(readOnly = true)
    public ResolvedPlatformConfig resolveActivePlatformConfig(PlatformConfigType configType) {
        PlatformConfigVersion config = platformConfigVersionRepository
                .findFirstByConfigTypeAndEffectiveDateLessThanEqualOrderByEffectiveDateDescVersionDesc(
                        configType,
                        OffsetDateTime.now()
                )
                .orElse(null);

        if (config == null) {
            return new ResolvedPlatformConfig(configType, null, EMPTY_JSON_OBJECT);
        }

        return new ResolvedPlatformConfig(
                configType,
                config.getVersion(),
                config.getPayload()
        );
    }

    public ObjectNode payloadAsObjectNode(ResolvedConfig resolvedConfig) {
        return readObjectPayload(resolvedConfig.payload());
    }

    public ObjectNode payloadAsObjectNode(ResolvedPlatformConfig resolvedConfig) {
        return readObjectPayload(resolvedConfig.payload());
    }

    private List<PlatformConfigType> mapFallbackTypes(CountryConfigType configType) {
        return switch (configType) {
            case GENERAL -> List.of(PlatformConfigType.GLOBAL_GENERAL, PlatformConfigType.GLOBAL_ALLOCATION);
            case MLM_LIMITS -> List.of(PlatformConfigType.GLOBAL_MLM_BASE_RATES);
            case MEMBERSHIP, GUARANTEE, FINANCING, ACCOUNTING, PAYMENT -> List.of();
        };
    }

    private String normalizeCountryCode(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            throw new IllegalArgumentException("Country code is required");
        }
        return countryCode.trim().toUpperCase(Locale.ROOT);
    }

    private ObjectNode readObjectPayload(String rawPayload) {
        try {
            JsonNode parsed = objectMapper.readTree(rawPayload == null || rawPayload.isBlank() ? EMPTY_JSON_OBJECT : rawPayload);
            return parsed.isObject() ? (ObjectNode) parsed : objectMapper.createObjectNode();
        } catch (JsonProcessingException ex) {
            ObjectNode fallback = objectMapper.createObjectNode();
            fallback.put("legacyRaw", rawPayload);
            return fallback;
        }
    }

    private void deepMergeInto(ObjectNode target, ObjectNode override) {
        override.fields().forEachRemaining(entry -> {
            String field = entry.getKey();
            JsonNode overrideValue = entry.getValue();
            JsonNode existingValue = target.get(field);
            if (existingValue != null && existingValue.isObject() && overrideValue.isObject()) {
                deepMergeInto((ObjectNode) existingValue, (ObjectNode) overrideValue);
                return;
            }
            target.set(field, overrideValue.deepCopy());
        });
    }

    private String writePayload(ObjectNode payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return EMPTY_JSON_OBJECT;
        }
    }

    private Integer maxVersion(Integer current, Integer candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null || candidate > current) {
            return candidate;
        }
        return current;
    }

    public enum ConfigSource {
        COUNTRY,
        MERGED,
        PLATFORM,
        EMPTY
    }

    public record ResolvedConfig(
            String countryCode,
            CountryConfigType configType,
            Integer version,
            String payload,
            ConfigSource source
    ) implements Serializable {
    }

    public record ResolvedPlatformConfig(
            PlatformConfigType configType,
            Integer version,
            String payload
    ) implements Serializable {
    }
}
