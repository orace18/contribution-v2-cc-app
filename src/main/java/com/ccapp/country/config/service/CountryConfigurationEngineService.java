package com.ccapp.country.config.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.dto.ActivateCountryConfigVersionRequest;
import com.ccapp.country.config.dto.CountryConfigFullResponse;
import com.ccapp.country.config.domain.entity.CountryConfigVersion;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.dto.CountryConfigSnapshotItemResponse;
import com.ccapp.country.config.dto.CountryConfigSnapshotResponse;
import com.ccapp.country.config.dto.CountryConfigVersionResponse;
import com.ccapp.country.config.dto.CountryConfigVersionHistoryItemResponse;
import com.ccapp.country.config.dto.UpsertCountryConfigRequest;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.config.repository.CountryConfigVersionRepository;
import com.ccapp.country.repository.CountryRepository;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CountryConfigurationEngineService {

    private static final int MAX_PAYLOAD_CHARS = 50_000;

    private final CountryConfigVersionRepository countryConfigVersionRepository;
    private final CountryRepository countryRepository;
    private final ObjectMapper objectMapper;

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

    @Transactional(readOnly = true)
    public CountryConfigFullResponse getFullCountryConfiguration(String countryCode, OffsetDateTime asOf) {
        CountryConfigSnapshotResponse snapshot = getCountryConfiguration(countryCode, asOf);

        return new CountryConfigFullResponse(
                payloadOf(snapshot, CountryConfigType.ACCOUNTING_CONFIG),
                payloadOf(snapshot, CountryConfigType.MLM_CONFIG),
                payloadOf(snapshot, CountryConfigType.MEMBERSHIP_CONFIG),
                payloadOf(snapshot, CountryConfigType.FINANCING_CONFIG),
                payloadOf(snapshot, CountryConfigType.PAYMENT_CONFIG),
                payloadOf(snapshot, CountryConfigType.CIRCLE_CONFIG),
                payloadOf(snapshot, CountryConfigType.RISK_CONFIG),
                payloadOf(snapshot, CountryConfigType.FEATURE_FLAGS)
        );
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(
                    cacheNames = CountryConfigResolver.COUNTRY_CONFIG_CACHE,
                    key = "'country_config:' + #countryCode.trim().toUpperCase(T(java.util.Locale).ROOT) + ':' + #configType.externalName()"
            )
    })
    public CountryConfigVersionResponse upsertCountryConfiguration(String countryCode,
                                                                  CountryConfigType configType,
                                                                  UpsertCountryConfigRequest request,
                                                                  UUID actorAdminUserId) {
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        assertCountryExists(normalizedCountryCode);
        JsonNode sanitizedPayload = validatePayload(configType, request.payload(), false);
        OffsetDateTime effectiveDate = request.effectiveDate() == null ? OffsetDateTime.now() : request.effectiveDate();
        validateCrossDomainRules(normalizedCountryCode, configType, sanitizedPayload, effectiveDate);
        CountryConfigVersion saved = saveNewVersion(
                normalizedCountryCode,
                configType,
                writePayload(sanitizedPayload),
                effectiveDate,
                actorAdminUserId
        );
        markCountryConfigured(normalizedCountryCode);
        return toVersionResponse(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = CountryConfigResolver.COUNTRY_CONFIG_CACHE, allEntries = true)
    public CountryConfigSnapshotResponse initializeCountryConfiguration(String countryCode, UUID actorAdminUserId) {
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        assertCountryExists(normalizedCountryCode);
        OffsetDateTime now = OffsetDateTime.now();
        String emptyObjectPayload = writePayload(objectMapper.createObjectNode());

        for (CountryConfigType type : CountryConfigType.values()) {
            boolean alreadyInitialized = countryConfigVersionRepository
                    .findFirstByCountryCodeAndConfigTypeOrderByVersionDesc(normalizedCountryCode, type)
                    .isPresent();
            if (!alreadyInitialized) {
                saveNewVersion(normalizedCountryCode, type, emptyObjectPayload, now, actorAdminUserId);
            }
        }

        markCountryConfigured(normalizedCountryCode);
        return getCountryConfiguration(normalizedCountryCode, now);
    }

    @Transactional(readOnly = true)
    public List<CountryConfigVersionHistoryItemResponse> listCountryConfigurationVersions(String countryCode,
                                                                                           CountryConfigType configType) {
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        assertCountryExists(normalizedCountryCode);

        Integer activeVersion = countryConfigVersionRepository
                .findFirstByCountryCodeAndConfigTypeAndEffectiveDateLessThanEqualOrderByEffectiveDateDescVersionDesc(
                        normalizedCountryCode, configType, OffsetDateTime.now()
                )
                .map(CountryConfigVersion::getVersion)
                .orElse(null);

        return countryConfigVersionRepository.findByCountryCodeAndConfigTypeOrderByVersionDesc(normalizedCountryCode, configType)
                .stream()
                .map(entity -> new CountryConfigVersionHistoryItemResponse(
                        entity.getVersion(),
                        entity.getEffectiveDate(),
                        readPayload(entity.getPayload()),
                        Objects.equals(entity.getVersion(), activeVersion),
                        entity.getCreatedByAdminUserId(),
                        entity.getCreatedAt()
                ))
                .toList();
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(
                    cacheNames = CountryConfigResolver.COUNTRY_CONFIG_CACHE,
                    key = "'country_config:' + #countryCode.trim().toUpperCase(T(java.util.Locale).ROOT) + ':' + #configType.externalName()"
            )
    })
    public CountryConfigVersionResponse activateCountryConfigurationVersion(String countryCode,
                                                                            CountryConfigType configType,
                                                                            ActivateCountryConfigVersionRequest request,
                                                                            UUID actorAdminUserId) {
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        assertCountryExists(normalizedCountryCode);

        CountryConfigVersion targetVersion = countryConfigVersionRepository
                .findByCountryCodeAndConfigTypeAndVersion(normalizedCountryCode, configType, request.version())
                .orElseThrow(() -> new BusinessException("Version not found for selected config type"));

        OffsetDateTime activationDate = request.effectiveDate() == null ? OffsetDateTime.now() : request.effectiveDate();
        validateCrossDomainRules(normalizedCountryCode, configType, readPayload(targetVersion.getPayload()), activationDate);
        CountryConfigVersion saved = saveNewVersion(
                normalizedCountryCode,
                configType,
                targetVersion.getPayload(),
                activationDate,
                actorAdminUserId
        );
        markCountryConfigured(normalizedCountryCode);
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

    private CountryConfigVersion saveNewVersion(String countryCode,
                                                CountryConfigType configType,
                                                String payload,
                                                OffsetDateTime effectiveDate,
                                                UUID actorAdminUserId) {
        int nextVersion = countryConfigVersionRepository.findFirstByCountryCodeAndConfigTypeOrderByVersionDesc(
                        countryCode,
                        configType
                )
                .map(existing -> existing.getVersion() + 1)
                .orElse(1);

        return countryConfigVersionRepository.save(CountryConfigVersion.builder()
                .countryCode(countryCode)
                .configType(configType)
                .version(nextVersion)
                .effectiveDate(effectiveDate == null ? OffsetDateTime.now() : effectiveDate)
                .payload(payload)
                .createdByAdminUserId(actorAdminUserId)
                .build());
    }

    private JsonNode payloadOf(CountryConfigSnapshotResponse snapshot, CountryConfigType configType) {
        return snapshot.configs().stream()
                .filter(item -> item.configType() == configType)
                .map(CountryConfigSnapshotItemResponse::payload)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseGet(objectMapper::createObjectNode);
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

    private JsonNode validatePayload(CountryConfigType configType, JsonNode payload, boolean allowEmptyObject) {
        if (payload == null || payload.isNull()) {
            throw new BusinessException("Configuration payload is required");
        }
        if (!payload.isObject()) {
            throw new BusinessException("Configuration payload must be a JSON object");
        }
        if (payload.size() == 0 && !allowEmptyObject) {
            throw new BusinessException("Configuration payload cannot be empty");
        }

        ObjectNode objectPayload = (ObjectNode) payload;
        String rawPayload = writePayload(payload);
        if (rawPayload.length() > MAX_PAYLOAD_CHARS) {
            throw new BusinessException("Configuration payload is too large");
        }

        if (payload.size() == 0) {
            return payload;
        }

        switch (configType) {
            case FEATURE_FLAGS -> validateFeatureFlags(objectPayload);
            case MLM_CONFIG -> validateMlmConfig(objectPayload);
            case MEMBERSHIP_CONFIG -> validateMembershipConfig(objectPayload);
            case ACCOUNTING_CONFIG -> validateAccountingConfig(objectPayload);
            case FINANCING_CONFIG -> validateFinancingConfig(objectPayload);
            case PAYMENT_CONFIG -> validatePaymentConfig(objectPayload);
            case CIRCLE_CONFIG -> validateCircleConfig(objectPayload);
            case RISK_CONFIG -> validateRiskConfig(objectPayload);
            default -> {
            }
        }

        return objectPayload;
    }

    private void validateFeatureFlags(ObjectNode payload) {
        payload.properties().forEach(entry -> {
            if (!entry.getValue().isBoolean()) {
                throw new BusinessException("feature_flags values must be boolean");
            }
        });
    }

    private void validateMlmConfig(ObjectNode payload) {
        int generations = requiredIntField(payload, "generations", 1, 20);
        JsonNode ratesNode = payload.get("commissionRates");
        if (ratesNode == null || !ratesNode.isArray() || ratesNode.isEmpty()) {
            throw new BusinessException("mlm_config.commissionRates must be a non-empty array");
        }
        Set<Integer> declaredGenerations = new HashSet<>();
        ratesNode.forEach(rateNode -> {
            if (!rateNode.isObject()) {
                throw new BusinessException("mlm_config.commissionRates entries must be objects");
            }
            ObjectNode rate = (ObjectNode) rateNode;
            int generation = requiredIntField(rate, "generation", 1, generations);
            if (!declaredGenerations.add(generation)) {
                throw new BusinessException("mlm_config.commissionRates has duplicate generation " + generation);
            }
            requiredDecimalField(rate, "rate", BigDecimal.ZERO, BigDecimal.ONE);
        });

        JsonNode capsNode = payload.get("caps");
        if (capsNode == null || !capsNode.isObject()) {
            throw new BusinessException("mlm_config.caps must be an object");
        }
        ObjectNode caps = (ObjectNode) capsNode;
        BigDecimal daily = requiredNonNegativeDecimal(caps, "daily");
        BigDecimal monthly = requiredNonNegativeDecimal(caps, "monthly");
        if (monthly.compareTo(daily) < 0) {
            throw new BusinessException("mlm_config.caps.monthly must be >= caps.daily");
        }
    }

    private void validateMembershipConfig(ObjectNode payload) {
        JsonNode plansNode = payload.get("plans");
        if (plansNode == null || !plansNode.isArray() || plansNode.isEmpty()) {
            throw new BusinessException("membership_config.plans must be a non-empty array");
        }
        plansNode.forEach(planNode -> {
            if (!planNode.isObject()) {
                throw new BusinessException("membership_config.plans entries must be objects");
            }
            ObjectNode plan = (ObjectNode) planNode;
            requiredTextField(plan, "name");
            JsonNode pricingNode = plan.get("pricing");
            if (pricingNode == null || !pricingNode.isObject()) {
                throw new BusinessException("membership_config.plan.pricing must be an object");
            }
            ObjectNode pricing = (ObjectNode) pricingNode;
            requiredNonNegativeDecimal(pricing, "amount");
            String currency = requiredTextField(pricing, "currency");
            if (currency.length() != 3) {
                throw new BusinessException("membership_config.plan.pricing.currency must be a 3-letter code");
            }

            JsonNode benefitsNode = plan.get("benefits");
            if (benefitsNode == null || !benefitsNode.isArray() || benefitsNode.isEmpty()) {
                throw new BusinessException("membership_config.plan.benefits must be a non-empty array");
            }
            benefitsNode.forEach(benefitNode -> {
                if (!benefitNode.isTextual() || benefitNode.asText().isBlank()) {
                    throw new BusinessException("membership_config.plan.benefits values must be non-empty strings");
                }
            });
        });
    }

    private void validateAccountingConfig(ObjectNode payload) {
        JsonNode preallocationAccountsNode = payload.get("preallocationAccounts");
        if (preallocationAccountsNode == null || !preallocationAccountsNode.isObject()) {
            throw new BusinessException("accounting_config.preallocationAccounts must be an object");
        }
        ObjectNode accounts = (ObjectNode) preallocationAccountsNode;
        if (accounts.isEmpty()) {
            throw new BusinessException("accounting_config.preallocationAccounts cannot be empty");
        }
        accounts.properties().forEach(entry -> {
            if (!entry.getValue().isTextual() || entry.getValue().asText().isBlank()) {
                throw new BusinessException("accounting_config.preallocationAccounts values must be non-empty strings");
            }
        });
    }

    private void validateFinancingConfig(ObjectNode payload) {
        BigDecimal minAmount = optionalDecimalField(payload, "minAmount");
        BigDecimal maxAmount = optionalDecimalField(payload, "maxAmount");
        if (minAmount != null && minAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("financing_config.minAmount must be >= 0");
        }
        if (maxAmount != null && maxAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("financing_config.maxAmount must be >= 0");
        }
        if (minAmount != null && maxAmount != null && maxAmount.compareTo(minAmount) < 0) {
            throw new BusinessException("financing_config.maxAmount must be >= minAmount");
        }
        BigDecimal maxInterestRate = optionalDecimalField(payload, "maxInterestRate");
        if (maxInterestRate != null && (maxInterestRate.compareTo(BigDecimal.ZERO) < 0
                || maxInterestRate.compareTo(new BigDecimal("100")) > 0)) {
            throw new BusinessException("financing_config.maxInterestRate must be between 0 and 100");
        }
    }

    private void validatePaymentConfig(ObjectNode payload) {
        Integer settlementWindowHours = optionalIntField(payload, "settlementWindowHours");
        if (settlementWindowHours != null && settlementWindowHours < 0) {
            throw new BusinessException("payment_config.settlementWindowHours must be >= 0");
        }
        BigDecimal feeRate = optionalDecimalField(payload, "feeRate");
        if (feeRate != null && (feeRate.compareTo(BigDecimal.ZERO) < 0 || feeRate.compareTo(BigDecimal.ONE) > 0)) {
            throw new BusinessException("payment_config.feeRate must be between 0 and 1");
        }
        ensureStringArray(payload, "methods", "payment_config.methods must be an array of non-empty strings");
        ensureStringArray(payload, "allowedMethods", "payment_config.allowedMethods must be an array of non-empty strings");
        ensureStringArray(payload, "pspMethods", "payment_config.pspMethods must be an array of non-empty strings");
    }

    private void validateCircleConfig(ObjectNode payload) {
        Integer minMembers = optionalIntField(payload, "minMembers");
        Integer maxMembers = optionalIntField(payload, "maxMembers");
        Integer maxAssistedMembers = optionalIntField(payload, "maxAssistedMembers");
        if (minMembers != null && minMembers < 1) {
            throw new BusinessException("circle_config.minMembers must be >= 1");
        }
        if (maxMembers != null && maxMembers < 1) {
            throw new BusinessException("circle_config.maxMembers must be >= 1");
        }
        if (maxMembers != null && maxMembers > 5) {
            throw new BusinessException("circle_config.maxMembers must be <= 5");
        }
        if (minMembers != null && maxMembers != null && maxMembers < minMembers) {
            throw new BusinessException("circle_config.maxMembers must be >= minMembers");
        }
        if (maxAssistedMembers != null && maxAssistedMembers < 0) {
            throw new BusinessException("circle_config.maxAssistedMembers must be >= 0");
        }
    }

    private void validateRiskConfig(ObjectNode payload) {
        BigDecimal maxExposure = optionalDecimalField(payload, "maxExposure");
        if (maxExposure != null && maxExposure.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("risk_config.maxExposure must be >= 0");
        }
        BigDecimal maxDefaultRate = optionalDecimalField(payload, "maxDefaultRate");
        if (maxDefaultRate != null && (maxDefaultRate.compareTo(BigDecimal.ZERO) < 0
                || maxDefaultRate.compareTo(BigDecimal.ONE) > 0)) {
            throw new BusinessException("risk_config.maxDefaultRate must be between 0 and 1");
        }
    }

    private String requiredTextField(ObjectNode payload, String fieldName) {
        JsonNode value = payload.get(fieldName);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new BusinessException(fieldName + " is required and must be a non-empty string");
        }
        return value.asText().trim();
    }

    private int requiredIntField(ObjectNode payload, String fieldName, int min, int max) {
        JsonNode value = payload.get(fieldName);
        if (value == null || !value.canConvertToInt()) {
            throw new BusinessException(fieldName + " is required and must be an integer");
        }
        int intValue = value.intValue();
        if (intValue < min || intValue > max) {
            throw new BusinessException(fieldName + " must be between " + min + " and " + max);
        }
        return intValue;
    }

    private BigDecimal requiredDecimalField(ObjectNode payload,
                                            String fieldName,
                                            BigDecimal minInclusive,
                                            BigDecimal maxInclusive) {
        JsonNode value = payload.get(fieldName);
        if (value == null || !value.isNumber()) {
            throw new BusinessException(fieldName + " is required and must be numeric");
        }
        BigDecimal decimal = value.decimalValue();
        if (decimal.compareTo(minInclusive) < 0 || decimal.compareTo(maxInclusive) > 0) {
            throw new BusinessException(fieldName + " must be between " + minInclusive + " and " + maxInclusive);
        }
        return decimal;
    }

    private BigDecimal requiredNonNegativeDecimal(ObjectNode payload, String fieldName) {
        JsonNode value = payload.get(fieldName);
        if (value == null || !value.isNumber()) {
            throw new BusinessException(fieldName + " is required and must be numeric");
        }
        BigDecimal decimal = value.decimalValue();
        if (decimal.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(fieldName + " must be >= 0");
        }
        return decimal;
    }

    private Integer optionalIntField(ObjectNode payload, String fieldName) {
        JsonNode value = payload.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.canConvertToInt()) {
            throw new BusinessException(fieldName + " must be an integer");
        }
        return value.intValue();
    }

    private BigDecimal optionalDecimalField(ObjectNode payload, String fieldName) {
        JsonNode value = payload.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isNumber()) {
            throw new BusinessException(fieldName + " must be numeric");
        }
        return value.decimalValue();
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

    private void validateCrossDomainRules(String countryCode,
                                          CountryConfigType targetType,
                                          JsonNode targetPayload,
                                          OffsetDateTime effectiveDate) {
        Map<CountryConfigType, ObjectNode> payloadByType = new HashMap<>();
        for (CountryConfigType type : CountryConfigType.values()) {
            payloadByType.put(type, resolvePayloadForValidation(countryCode, type, targetType, targetPayload, effectiveDate));
        }

        validateMlmMembershipAlignment(payloadByType.get(CountryConfigType.MLM_CONFIG),
                payloadByType.get(CountryConfigType.MEMBERSHIP_CONFIG));
        validateFinancingRiskAlignment(payloadByType.get(CountryConfigType.FINANCING_CONFIG),
                payloadByType.get(CountryConfigType.RISK_CONFIG));
        validatePaymentMethodsAgainstPspConfig(payloadByType.get(CountryConfigType.PAYMENT_CONFIG));
    }

    private ObjectNode resolvePayloadForValidation(String countryCode,
                                                   CountryConfigType inspectedType,
                                                   CountryConfigType targetType,
                                                   JsonNode targetPayload,
                                                   OffsetDateTime effectiveDate) {
        if (inspectedType == targetType) {
            return toObjectNode(targetPayload);
        }

        return countryConfigVersionRepository
                .findFirstByCountryCodeAndConfigTypeAndEffectiveDateLessThanEqualOrderByEffectiveDateDescVersionDesc(
                        countryCode,
                        inspectedType,
                        effectiveDate
                )
                .map(CountryConfigVersion::getPayload)
                .map(this::readPayload)
                .map(this::toObjectNode)
                .orElseGet(objectMapper::createObjectNode);
    }

    private void validateMlmMembershipAlignment(ObjectNode mlmConfig, ObjectNode membershipConfig) {
        JsonNode generationsNode = mlmConfig.get("generations");
        JsonNode commissionRatesNode = mlmConfig.get("commissionRates");
        boolean mlmConfigured = (generationsNode != null && generationsNode.isNumber())
                || (commissionRatesNode != null && commissionRatesNode.isArray() && !commissionRatesNode.isEmpty());
        if (!mlmConfigured) {
            return;
        }

        JsonNode plansNode = membershipConfig.get("plans");
        if (plansNode == null || !plansNode.isArray() || plansNode.isEmpty()) {
            throw new BusinessException("membership_config.plans must be configured before enabling mlm_config");
        }
    }

    private void validateFinancingRiskAlignment(ObjectNode financingConfig, ObjectNode riskConfig) {
        BigDecimal financingMaxAmount = optionalDecimalField(financingConfig, "maxAmount");
        BigDecimal riskMaxExposure = optionalDecimalField(riskConfig, "maxExposure");
        if (financingMaxAmount == null || riskMaxExposure == null) {
            return;
        }
        if (financingMaxAmount.compareTo(riskMaxExposure) > 0) {
            throw new BusinessException("financing_config.maxAmount must be <= risk_config.maxExposure");
        }
    }

    private void validatePaymentMethodsAgainstPspConfig(ObjectNode paymentConfig) {
        Set<String> methods = readStringSet(paymentConfig, "methods");
        if (methods.isEmpty()) {
            methods = readStringSet(paymentConfig, "allowedMethods");
        }
        if (methods.isEmpty()) {
            return;
        }

        Set<String> pspMethods = readStringSet(paymentConfig, "pspMethods");
        if (pspMethods.isEmpty()) {
            throw new BusinessException("payment_config.pspMethods must be configured when payment methods are defined");
        }
        if (!pspMethods.containsAll(methods)) {
            throw new BusinessException("payment_config methods must exist in country PSP config");
        }
    }

    private void ensureStringArray(ObjectNode payload, String fieldName, String errorMessage) {
        JsonNode node = payload.get(fieldName);
        if (node == null || node.isNull()) {
            return;
        }
        if (!node.isArray()) {
            throw new BusinessException(errorMessage);
        }
        node.forEach(item -> {
            if (!item.isTextual() || item.asText().isBlank()) {
                throw new BusinessException(errorMessage);
            }
        });
    }

    private Set<String> readStringSet(ObjectNode payload, String fieldName) {
        JsonNode node = payload.get(fieldName);
        Set<String> values = new HashSet<>();
        if (node == null || !node.isArray()) {
            return values;
        }
        node.forEach(item -> {
            if (!item.isTextual() || item.asText().isBlank()) {
                throw new BusinessException(fieldName + " values must be non-empty strings");
            }
            values.add(item.asText().trim().toUpperCase(Locale.ROOT));
        });
        return values;
    }

    private ObjectNode toObjectNode(JsonNode payload) {
        if (payload != null && payload.isObject()) {
            return (ObjectNode) payload;
        }
        return objectMapper.createObjectNode();
    }

    private void assertCountryExists(String countryCode) {
        if (countryRepository.findByCode(countryCode).isEmpty()) {
            throw new BusinessException("Country not found");
        }
    }

    private void markCountryConfigured(String countryCode) {
        Country country = countryRepository.findByCode(countryCode)
                .orElseThrow(() -> new BusinessException("Country not found"));
        boolean changed = false;
        if (!country.isConfigured()) {
            country.setConfigured(true);
            changed = true;
        }
        if (!country.isActive()) {
            country.setActive(true);
            changed = true;
        }
        if (changed) {
            countryRepository.save(country);
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
