package com.ccapp.country.config.service;

import com.ccapp.admin.service.ConfigAuditMetadataService;
import com.ccapp.common.dto.ConfigAuditMetadataResponse;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.domain.enums.CountryAccountSystemTag;
import com.ccapp.country.config.dto.ActivateCountryConfigVersionRequest;
import com.ccapp.country.config.dto.CountryConfigFullResponse;
import com.ccapp.country.config.domain.enums.CountryChartAccountStatus;
import com.ccapp.country.config.domain.entity.CountryConfigVersion;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.dto.CountryConfigSnapshotItemResponse;
import com.ccapp.country.config.dto.CountryConfigSnapshotResponse;
import com.ccapp.country.config.dto.CountryConfigReadinessResponse;
import com.ccapp.country.config.dto.CountryConfigReadinessStatus;
import com.ccapp.country.config.dto.CountryConfigValidationErrorResponse;
import com.ccapp.country.config.dto.CountryConfigValidationResponse;
import com.ccapp.country.config.dto.CountryConfigVersionResponse;
import com.ccapp.country.config.dto.CountryConfigVersionHistoryItemResponse;
import com.ccapp.country.config.dto.CountrySetupStatusResponse;
import com.ccapp.country.config.dto.UpsertCountryConfigRequest;
import com.ccapp.country.config.repository.CountryChartAccountRepository;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.config.repository.CountryConfigVersionRepository;
import com.ccapp.country.repository.CountryRepository;
import com.ccapp.payments.subscription.service.SubscriptionPlanCatalogService;
import com.ccapp.payments.subscription.service.SubscriptionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
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
@Slf4j
public class CountryConfigurationEngineService {

    private static final int MAX_PAYLOAD_CHARS = 50_000;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final String COUNTRY_CONFIG_ENTITY_TYPE = "COUNTRY_CONFIG";
    private static final String JAWUPAY_PROVIDER = "JAWUPAY";
    private static final String JAWUPAY_METHOD_SOURCE = "JAWUPAY_DYNAMIC";
    private static final List<CountryConfigType> SETUP_VALIDATION_TYPES = List.of(
            CountryConfigType.GENERAL,
            CountryConfigType.ACCOUNTING,
            CountryConfigType.MEMBERSHIP,
            CountryConfigType.MLM_LIMITS,
            CountryConfigType.FINANCING,
            CountryConfigType.GUARANTEE
    );

    private final CountryConfigVersionRepository countryConfigVersionRepository;
    private final CountryChartAccountRepository countryChartAccountRepository;
    private final CountryRepository countryRepository;
    private final CountryChartOfAccountsService countryChartOfAccountsService;
    private final CountryConfigReadinessService countryConfigReadinessService;
    private final SubscriptionPlanCatalogService subscriptionPlanCatalogService;
    private final SubscriptionService subscriptionService;
    private final ConfigAuditMetadataService configAuditMetadataService;
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
                        .orElse(new CountryConfigSnapshotItemResponse(configType, null, null, null, emptyAudit("DRAFT")))
                )
                .toList();

        return new CountryConfigSnapshotResponse(normalizedCountryCode, asOfDate, configs);
    }

    @Transactional(readOnly = true)
    public CountryConfigFullResponse getFullCountryConfiguration(String countryCode, OffsetDateTime asOf) {
        CountryConfigSnapshotResponse snapshot = getCountryConfiguration(countryCode, asOf);

        return new CountryConfigFullResponse(
                payloadOf(snapshot, CountryConfigType.GENERAL),
                payloadOf(snapshot, CountryConfigType.MEMBERSHIP),
                payloadOf(snapshot, CountryConfigType.MLM_LIMITS),
                payloadOf(snapshot, CountryConfigType.GUARANTEE),
                payloadOf(snapshot, CountryConfigType.FINANCING),
                payloadOf(snapshot, CountryConfigType.ACCOUNTING),
                buildManagedPaymentPayload()
        );
    }

    @Transactional(readOnly = true)
    public CountrySetupStatusResponse getSetupStatus(String countryCode) {
        String normalized = normalizeCountryCode(countryCode);
        assertCountryExists(normalized);

        OffsetDateTime now = OffsetDateTime.now();
        Map<String, CountrySetupStatusResponse.StepStatus> steps = new HashMap<>();

        // general
        boolean generalDone = countryConfigVersionRepository
                .findFirstByCountryCodeAndConfigTypeAndEffectiveDateLessThanEqualOrderByEffectiveDateDescVersionDesc(
                        normalized, CountryConfigType.GENERAL, now)
                .map(v -> v.getPayload() != null && !v.getPayload().isEmpty())
                .orElse(false);
        steps.put("general", generalDone
                ? CountrySetupStatusResponse.StepStatus.ok()
                : CountrySetupStatusResponse.StepStatus.blocked("General configuration not saved"));

        // accounting: complete only when system accounts exist in DB
        boolean accountingDone = countryChartOfAccountsService.hasRequiredSystemMappings(normalized);
        steps.put("accounting", accountingDone
                ? CountrySetupStatusResponse.StepStatus.ok()
                : CountrySetupStatusResponse.StepStatus.blocked("System accounts not generated. Use 'Generate accounts' in Step 2."));

        // membership
        boolean membershipDone = countryConfigVersionRepository
                .findFirstByCountryCodeAndConfigTypeAndEffectiveDateLessThanEqualOrderByEffectiveDateDescVersionDesc(
                        normalized, CountryConfigType.MEMBERSHIP, now)
                .map(v -> v.getPayload() != null && !v.getPayload().isEmpty())
                .orElse(false);
        steps.put("membership", membershipDone
                ? CountrySetupStatusResponse.StepStatus.ok()
                : CountrySetupStatusResponse.StepStatus.blocked("Membership configuration not saved"));

        // mlm
        boolean mlmDone = countryConfigVersionRepository
                .findFirstByCountryCodeAndConfigTypeAndEffectiveDateLessThanEqualOrderByEffectiveDateDescVersionDesc(
                        normalized, CountryConfigType.MLM_LIMITS, now)
                .map(v -> v.getPayload() != null && !v.getPayload().isEmpty())
                .orElse(false);
        steps.put("mlm", mlmDone
                ? CountrySetupStatusResponse.StepStatus.ok()
                : CountrySetupStatusResponse.StepStatus.blocked("MLM configuration not saved"));

        // financing is optional and never blocks activation.
        boolean financingDone = countryConfigVersionRepository
                .findFirstByCountryCodeAndConfigTypeAndEffectiveDateLessThanEqualOrderByEffectiveDateDescVersionDesc(
                        normalized, CountryConfigType.FINANCING, now)
                .map(v -> v.getPayload() != null && !v.getPayload().isEmpty())
                .orElse(false);
        steps.put("financing", financingDone
                ? CountrySetupStatusResponse.StepStatus.ok()
                : new CountrySetupStatusResponse.StepStatus(true, "Financing not configured (optional)"));

        // guarantee
        boolean guaranteeDone = countryConfigVersionRepository
                .findFirstByCountryCodeAndConfigTypeAndEffectiveDateLessThanEqualOrderByEffectiveDateDescVersionDesc(
                        normalized, CountryConfigType.GUARANTEE, now)
                .map(v -> v.getPayload() != null && !v.getPayload().isEmpty())
                .orElse(false);
        steps.put("guarantee", guaranteeDone
                ? CountrySetupStatusResponse.StepStatus.ok()
                : CountrySetupStatusResponse.StepStatus.blocked("Risk & guarantee configuration not saved"));

        boolean canActivate = steps.values().stream().allMatch(CountrySetupStatusResponse.StepStatus::complete);
        return new CountrySetupStatusResponse(steps, canActivate);
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
        if (configType == CountryConfigType.MEMBERSHIP) {
            subscriptionPlanCatalogService.syncCountryCatalogFromMembershipConfig(normalizedCountryCode, sanitizedPayload);
        }
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
                String initialPayload = type == CountryConfigType.PAYMENT
                        ? writePayload(buildManagedPaymentPayload())
                        : emptyObjectPayload;
                saveNewVersion(normalizedCountryCode, type, initialPayload, now, actorAdminUserId);
            }
        }

        countryChartOfAccountsService.autoGenerateInitializationAccounts(normalizedCountryCode);
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
                .map(entity -> {
                    boolean isActive = Objects.equals(entity.getVersion(), activeVersion);
                    ConfigAuditMetadataResponse audit = configAuditMetadataService.build(
                            COUNTRY_CONFIG_ENTITY_TYPE,
                            entity.getId(),
                            entity.getVersion(),
                            isActive ? "ACTIVE" : "INACTIVE",
                            entity.getEffectiveDate(),
                            entity.getCreatedAt(),
                            entity.getCreatedByAdminUserId()
                    );
                    return new CountryConfigVersionHistoryItemResponse(
                            entity.getVersion(),
                            entity.getEffectiveDate(),
                            readPayload(entity.getPayload()),
                            isActive,
                            entity.getCreatedByAdminUserId(),
                            entity.getCreatedAt(),
                            audit
                    );
                })
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
        countryChartOfAccountsService.autoGenerateRequiredAccounts(normalizedCountryCode);
        countryChartOfAccountsService.assertCriticalSystemMappingsExist(normalizedCountryCode);
        markCountryConfigured(normalizedCountryCode);
        return toVersionResponse(saved);
    }

    @Transactional
    public CountryConfigValidationResponse validateCountrySetup(String countryCode,
                                                                Map<CountryConfigType, JsonNode> payloads,
                                                                OffsetDateTime effectiveDate) {
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        assertCountryExists(normalizedCountryCode);

        Map<CountryConfigType, ObjectNode> sanitizedPayloads = new HashMap<>();
        List<CountryConfigValidationErrorResponse> errors = new ArrayList<>();

        for (Map.Entry<CountryConfigType, JsonNode> entry : payloads.entrySet()) {
            try {
                JsonNode sanitizedPayload = validatePayload(entry.getKey(), entry.getValue(), false);
                sanitizedPayloads.put(entry.getKey(), toObjectNode(sanitizedPayload));
            } catch (BusinessException ex) {
                errors.add(new CountryConfigValidationErrorResponse(
                        entry.getKey().externalName(),
                        ex.getMessage()
                ));
            }
        }

        if (!errors.isEmpty()) {
            return new CountryConfigValidationResponse(false, errors);
        }

        SetupValidationResult validationResult = evaluateSetupValidation(normalizedCountryCode, sanitizedPayloads);
        errors.addAll(validationResult.errors());

        boolean valid = errors.isEmpty();
        // Business rule: validation is the single source of truth for configured status.
        // If validation passes, mark the country as configured unconditionally.
        if (valid) {
            persistConfiguredByValidation(normalizedCountryCode);
            ensureConfiguredInvariant(normalizedCountryCode);
        }
        return new CountryConfigValidationResponse(valid, errors);
    }

    /**
     * Marks the country as configured after a successful validation call.
     * Validation is the authoritative source of truth for configured status —
     * this write must happen regardless of the runtime readiness score.
     */
    private void persistConfiguredByValidation(String countryCode) {
        Country country = countryRepository.findByCode(countryCode)
                .orElseThrow(() -> new BusinessException("Country not found"));
        boolean changed = false;
        if (!country.isConfigured()) {
            country.setConfigured(true);
            log.info("[VALIDATION] country={} marked configured=true (validation passed)", countryCode);
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

    @Transactional
    public boolean refreshCountryConfiguredState(String countryCode) {
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        return synchronizeCountryConfiguredState(normalizedCountryCode, false);
    }

    @Transactional
    public CountryConfigReadinessResponse getCountryReadiness(String countryCode) {
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        CountryConfigReadinessResponse readiness = countryConfigReadinessService.evaluateReadiness(normalizedCountryCode);
        boolean configured = synchronizeCountryConfiguredState(normalizedCountryCode, false);
        return new CountryConfigReadinessResponse(
                configured,
                readiness.score(),
                configured ? CountryConfigReadinessStatus.CONFIGURED
                        : CountryConfigReadinessStatus.NOT_CONFIGURED,
                readiness.missing(),
                readiness.sections()
        );
    }

    private SetupValidationResult evaluateSetupValidation(String countryCode,
                                                          Map<CountryConfigType, ObjectNode> payloads) {
        Map<CountryConfigType, ObjectNode> normalizedPayloads = setupPayloadsWithDefaults(payloads);
        CountryConfigReadinessResponse readiness = countryConfigReadinessService.evaluateReadiness(countryCode, normalizedPayloads);
        if (!readiness.configured()) {
            return new SetupValidationResult(false, buildReadinessValidationErrors(readiness));
        }

        List<CountryConfigValidationErrorResponse> errors = new ArrayList<>();
        try {
            validateMlmMembershipAlignment(
                    normalizedPayloads.get(CountryConfigType.MLM_LIMITS),
                    normalizedPayloads.get(CountryConfigType.MEMBERSHIP)
            );
            validateGuaranteeMembershipAlignment(
                    normalizedPayloads.get(CountryConfigType.GUARANTEE),
                    normalizedPayloads.get(CountryConfigType.MEMBERSHIP)
            );
            validateGuaranteeFinancingAlignment(
                    normalizedPayloads.get(CountryConfigType.GUARANTEE),
                    normalizedPayloads.get(CountryConfigType.FINANCING)
            );
            subscriptionPlanCatalogService.assertMembershipPayloadCompatibleWithCountry(
                    countryCode,
                    normalizedPayloads.get(CountryConfigType.MEMBERSHIP)
            );
            validateAccountingAccountsExist(
                    countryCode,
                    normalizedPayloads.get(CountryConfigType.ACCOUNTING)
            );
        } catch (BusinessException ex) {
            errors.add(new CountryConfigValidationErrorResponse(
                    resolveValidationField(ex.getMessage()),
                    ex.getMessage()
            ));
        }
        return new SetupValidationResult(errors.isEmpty(), errors);
    }

    private List<CountryConfigValidationErrorResponse> buildReadinessValidationErrors(CountryConfigReadinessResponse readiness) {
        return readiness.missing().stream()
                .distinct()
                .map(message -> new CountryConfigValidationErrorResponse(resolveValidationField(message), message))
                .toList();
    }

    private Map<CountryConfigType, ObjectNode> setupPayloadsWithDefaults(Map<CountryConfigType, ObjectNode> payloads) {
        Map<CountryConfigType, ObjectNode> normalized = new EnumMap<>(CountryConfigType.class);
        for (CountryConfigType type : SETUP_VALIDATION_TYPES) {
            ObjectNode payload = payloads == null ? null : payloads.get(type);
            normalized.put(type, payload == null ? objectMapper.createObjectNode() : payload);
        }
        return normalized;
    }

    private Map<CountryConfigType, ObjectNode> resolveActiveSetupPayloads(String countryCode) {
        Map<CountryConfigType, ObjectNode> payloads = new EnumMap<>(CountryConfigType.class);
        OffsetDateTime now = OffsetDateTime.now();
        for (CountryConfigType type : SETUP_VALIDATION_TYPES) {
            ObjectNode payload = countryConfigVersionRepository
                    .findFirstByCountryCodeAndConfigTypeAndEffectiveDateLessThanEqualOrderByEffectiveDateDescVersionDesc(
                            countryCode,
                            type,
                            now
                    )
                    .map(CountryConfigVersion::getPayload)
                    .map(this::readPayload)
                    .map(this::toObjectNode)
                    .orElseGet(objectMapper::createObjectNode);
            payloads.put(type, payload);
        }
        return payloads;
    }

    private boolean synchronizeCountryConfiguredState(String countryCode, boolean activateCountry) {
        Country country = countryRepository.findByCode(countryCode)
                .orElseThrow(() -> new BusinessException("Country not found"));
        SetupValidationResult validationResult = evaluateSetupValidation(countryCode, resolveActiveSetupPayloads(countryCode));
        boolean configured = validationResult.valid();
        if (configured) {
            subscriptionService.assertCountryPlanCatalogValid(countryCode);
        }

        boolean changed = false;
        if (country.isConfigured() != configured) {
            country.setConfigured(configured);
            changed = true;
        }
        if (activateCountry && !country.isActive()) {
            country.setActive(true);
            changed = true;
        }
        if (changed) {
            countryRepository.save(country);
        }
        return configured;
    }

    private void ensureConfiguredInvariant(String countryCode) {
        Country country = countryRepository.findByCode(countryCode)
                .orElseThrow(() -> new BusinessException("Country not found"));
        if (!country.isConfigured()) {
            country.setConfigured(true);
            countryRepository.save(country);
        }
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
        ConfigAuditMetadataResponse audit = configAuditMetadataService.build(
                COUNTRY_CONFIG_ENTITY_TYPE,
                entity.getId(),
                entity.getVersion(),
                "ACTIVE",
                entity.getEffectiveDate(),
                entity.getCreatedAt(),
                entity.getCreatedByAdminUserId()
        );
        return new CountryConfigSnapshotItemResponse(
                entity.getConfigType(),
                entity.getVersion(),
                entity.getEffectiveDate(),
                readPayload(entity.getPayload()),
                audit
        );
    }

    private CountryConfigVersionResponse toVersionResponse(CountryConfigVersion entity) {
        ConfigAuditMetadataResponse audit = configAuditMetadataService.build(
                COUNTRY_CONFIG_ENTITY_TYPE,
                entity.getId(),
                entity.getVersion(),
                resolveStatus(entity),
                entity.getEffectiveDate(),
                entity.getCreatedAt(),
                entity.getCreatedByAdminUserId()
        );
        return new CountryConfigVersionResponse(
                entity.getId(),
                entity.getCountryCode(),
                entity.getConfigType(),
                entity.getVersion(),
                entity.getEffectiveDate(),
                readPayload(entity.getPayload()),
                entity.getCreatedByAdminUserId(),
                entity.getCreatedAt(),
                audit
        );
    }

    private String resolveStatus(CountryConfigVersion entity) {
        Integer activeVersion = countryConfigVersionRepository
                .findFirstByCountryCodeAndConfigTypeAndEffectiveDateLessThanEqualOrderByEffectiveDateDescVersionDesc(
                        entity.getCountryCode(),
                        entity.getConfigType(),
                        OffsetDateTime.now()
                )
                .map(CountryConfigVersion::getVersion)
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

    private JsonNode validatePayload(CountryConfigType configType, JsonNode payload, boolean allowEmptyObject) {
        if (configType == CountryConfigType.PAYMENT) {
            return buildManagedPaymentPayload();
        }
        if (payload == null || payload.isNull()) {
            throw new BusinessException("Configuration payload is required");
        }
        if (!payload.isObject()) {
            throw new BusinessException("Configuration payload must be a JSON object");
        }
        if (payload.size() == 0 && !allowEmptyObject && configType != CountryConfigType.ACCOUNTING) {
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
            case GENERAL -> validateGeneralConfig(objectPayload);
            case MEMBERSHIP -> validateMembershipConfig(objectPayload);
            case MLM_LIMITS -> validateMlmLimitsConfig(objectPayload);
            case GUARANTEE -> validateGuaranteeConfig(objectPayload);
            case FINANCING -> validateFinancingConfig(objectPayload);
            case ACCOUNTING -> validateAccountingConfig(objectPayload);
            case PAYMENT -> validatePaymentConfig(objectPayload);
        }

        return objectPayload;
    }

    private void validateGeneralConfig(ObjectNode payload) {
        optionalPercentageField(payload, "taxRate", "general.taxRate");
        optionalPercentageField(payload, "marketplaceDefaultCommission", "general.marketplaceDefaultCommission");

        ObjectNode circle = payload.has("circle") && payload.get("circle").isObject()
                ? (ObjectNode) payload.get("circle")
                : payload;
        Integer minMembers = optionalIntField(circle, "minMembers");
        Integer maxMembers = optionalIntField(circle, "maxMembers");
        Integer maxAssistedMembers = optionalIntField(circle, "maxAssistedMembers");
        if (minMembers != null && minMembers < 1) {
            throw new BusinessException("general.circle.minMembers must be >= 1");
        }
        if (maxMembers != null && maxMembers < 1) {
            throw new BusinessException("general.circle.maxMembers must be >= 1");
        }
        if (minMembers != null && maxMembers != null && maxMembers < minMembers) {
            throw new BusinessException("general.circle.maxMembers must be >= minMembers");
        }
        if (maxAssistedMembers != null && maxAssistedMembers < 0) {
            throw new BusinessException("general.circle.maxAssistedMembers must be >= 0");
        }

        JsonNode featureFlagsNode = payload.get("features");
        if (featureFlagsNode != null && !featureFlagsNode.isNull()) {
            if (!featureFlagsNode.isObject()) {
                throw new BusinessException("general.features must be an object");
            }
            ObjectNode featureFlags = (ObjectNode) featureFlagsNode;
            featureFlags.properties().forEach(entry -> {
                if (!entry.getValue().isBoolean()) {
                    throw new BusinessException("general.features values must be boolean");
                }
            });
        }

        JsonNode walletSecurityNode = payload.get("walletSecurity");
        if (walletSecurityNode != null && !walletSecurityNode.isNull()) {
            if (!walletSecurityNode.isObject()) {
                throw new BusinessException("general.walletSecurity must be an object");
            }
            ObjectNode walletSecurity = (ObjectNode) walletSecurityNode;
            JsonNode pinRequired = walletSecurity.get("pinRequired");
            if (pinRequired != null && !pinRequired.isNull() && !pinRequired.isBoolean()) {
                throw new BusinessException("general.walletSecurity.pinRequired must be boolean");
            }
            BigDecimal otpThreshold = optionalDecimalField(walletSecurity, "otpThreshold");
            if (otpThreshold != null && otpThreshold.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("general.walletSecurity.otpThreshold must be > 0");
            }
            Integer pinMaxAttempts = optionalIntField(walletSecurity, "pinMaxAttempts");
            if (pinMaxAttempts != null && pinMaxAttempts < 1) {
                throw new BusinessException("general.walletSecurity.pinMaxAttempts must be >= 1");
            }
            Integer otpMaxAttempts = optionalIntField(walletSecurity, "otpMaxAttempts");
            if (otpMaxAttempts != null && otpMaxAttempts < 1) {
                throw new BusinessException("general.walletSecurity.otpMaxAttempts must be >= 1");
            }
            Integer otpTtlSeconds = optionalIntField(walletSecurity, "otpTtlSeconds");
            if (otpTtlSeconds != null && otpTtlSeconds < 30) {
                throw new BusinessException("general.walletSecurity.otpTtlSeconds must be >= 30");
            }
        }
    }

    private void validateMembershipConfig(ObjectNode payload) {
        subscriptionPlanCatalogService.assertMembershipPayloadCatalogValid(payload);
    }

    private void validateMlmLimitsConfig(ObjectNode payload) {
        Integer maxDirectChildren = optionalIntField(payload, "maxDirectChildren");
        if (maxDirectChildren != null && maxDirectChildren < 1) {
            throw new BusinessException("mlm_limits.maxDirectChildren must be >= 1");
        }

        BigDecimal weeklyCap = optionalDecimalField(payload, "weeklyCap");
        if (weeklyCap != null && weeklyCap.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("mlm_limits.weeklyCap must be >= 0");
        }

        JsonNode weeklyCapsByTierNode = payload.get("weeklyCapsByTier");
        if (weeklyCapsByTierNode != null && !weeklyCapsByTierNode.isNull()) {
            if (!weeklyCapsByTierNode.isArray() || weeklyCapsByTierNode.isEmpty()) {
                throw new BusinessException("mlm_limits.weeklyCapsByTier must be a non-empty array");
            }
            Set<String> tiers = new HashSet<>();
            weeklyCapsByTierNode.forEach(item -> {
                if (!item.isObject()) {
                    throw new BusinessException("mlm_limits.weeklyCapsByTier entries must be objects");
                }
                ObjectNode entry = (ObjectNode) item;
                String tier = requiredTextField(entry, "tier").trim().toUpperCase(Locale.ROOT);
                if (!tiers.add(tier)) {
                    throw new BusinessException("mlm_limits.weeklyCapsByTier duplicate tier: " + tier);
                }
                BigDecimal tierCap = requiredNonNegativeDecimal(entry, "cap");
                if (tierCap.compareTo(BigDecimal.ZERO) < 0) {
                    throw new BusinessException("mlm_limits.weeklyCapsByTier.cap must be >= 0");
                }
            });
        }

        rejectMlmRateOverride(payload, "baseRatesOverride");
        rejectMlmRateOverride(payload, "commissionRates");
        rejectMlmRateOverride(payload, "overrideUnilevelRate");
        rejectMlmRateOverride(payload, "overrideBinaryRate");
        rejectMlmRateOverride(payload, "usePlatformFallback");
    }

    private void rejectMlmRateOverride(ObjectNode payload, String fieldName) {
        JsonNode fieldNode = payload.get(fieldName);
        if (fieldNode != null && !fieldNode.isNull()) {
            throw new BusinessException("mlm_limits." + fieldName + " is not allowed. MLM rates are global only");
        }
    }

    private void validateGuaranteeConfig(ObjectNode payload) {
        BigDecimal globalMaxExposure = optionalDecimalField(payload, "globalMaxExposure");
        if (globalMaxExposure != null && globalMaxExposure.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("guarantee.globalMaxExposure must be >= 0");
        }

        JsonNode limitsByTierNode = payload.get("limitsByTier");
        if (limitsByTierNode == null || limitsByTierNode.isNull()) {
            return;
        }
        if (!limitsByTierNode.isArray() || limitsByTierNode.isEmpty()) {
            throw new BusinessException("guarantee.limitsByTier must be a non-empty array");
        }

        Set<String> declaredTiers = new HashSet<>();
        limitsByTierNode.forEach(limitNode -> {
            if (!limitNode.isObject()) {
                throw new BusinessException("guarantee.limitsByTier entries must be objects");
            }
            ObjectNode limit = (ObjectNode) limitNode;
            String tier = requiredTextField(limit, "tier").trim().toUpperCase(Locale.ROOT);
            if (!declaredTiers.add(tier)) {
                throw new BusinessException("guarantee.limitsByTier duplicate tier: " + tier);
            }
            requiredPositiveDecimal(limit, "maxAmount");
            BigDecimal maxExposure = optionalDecimalField(limit, "maxExposure");
            if (maxExposure != null && maxExposure.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("guarantee.limitsByTier.maxExposure must be >= 0");
            }
        });
    }

    private void validateAccountingConfig(ObjectNode payload) {
        if (payload.isEmpty()) {
            return;
        }
        JsonNode accountsNode = payload.has("accounts") ? payload.get("accounts") : payload.get("preallocationAccounts");
        if (accountsNode != null && !accountsNode.isNull()) {
            if (!accountsNode.isObject()) {
                throw new BusinessException("accounting.accounts must be an object");
            }
            ObjectNode accounts = (ObjectNode) accountsNode;
            if (accounts.isEmpty()) {
                throw new BusinessException("accounting.accounts cannot be empty");
            }
            accounts.properties().forEach(entry -> {
                if (!entry.getValue().isTextual() || entry.getValue().asText().isBlank()) {
                    throw new BusinessException("accounting.accounts values must be non-empty strings");
                }
                String accountCode = entry.getValue().asText().trim().toUpperCase(Locale.ROOT);
                if (!accountCode.matches("[A-Z0-9._-]{3,64}")) {
                    throw new BusinessException("Invalid accounting account code: " + entry.getKey());
                }
            });
            return;
        }

        JsonNode requiredSystemTags = payload.get("requiredSystemTags");
        if (requiredSystemTags != null && !requiredSystemTags.isNull()) {
            if (!requiredSystemTags.isArray()) {
                throw new BusinessException("accounting.requiredSystemTags must be an array");
            }
            requiredSystemTags.forEach(tagNode -> {
                if (!tagNode.isTextual() || tagNode.asText().isBlank()) {
                    throw new BusinessException("accounting.requiredSystemTags values must be non-empty strings");
                }
                try {
                    CountryAccountSystemTag.valueOf(tagNode.asText().trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    throw new BusinessException("Unsupported accounting system tag: " + tagNode.asText());
                }
            });
        }
        if (payload.has("mode")) {
            requiredTextField(payload, "mode");
        }
        if (payload.has("masterTemplate")) {
            requiredTextField(payload, "masterTemplate");
        }
    }

    private void validateFinancingConfig(ObjectNode payload) {
        BigDecimal minAmount = optionalDecimalField(payload, "minAmount");
        BigDecimal maxAmount = optionalDecimalField(payload, "maxAmount");
        if (minAmount != null && minAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("financing.minAmount must be >= 0");
        }
        if (maxAmount != null && maxAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("financing.maxAmount must be >= 0");
        }
        if (minAmount != null && maxAmount != null && maxAmount.compareTo(minAmount) < 0) {
            throw new BusinessException("financing.maxAmount must be >= minAmount");
        }
        BigDecimal maxInterestRate = optionalDecimalField(payload, "maxInterestRate");
        if (maxInterestRate != null && (maxInterestRate.compareTo(BigDecimal.ZERO) < 0 || maxInterestRate.compareTo(ONE_HUNDRED) > 0)) {
            throw new BusinessException("financing.maxInterestRate must be between 0 and 100");
        }

        JsonNode offersNode = payload.get("offers");
        if (offersNode == null || offersNode.isNull()) {
            return;
        }
        if (!offersNode.isArray()) {
            throw new BusinessException("financing.offers must be an array");
        }
        if (offersNode.isEmpty()) {
            return;
        }
        offersNode.forEach(offerNode -> {
            if (!offerNode.isObject()) {
                throw new BusinessException("financing.offers entries must be objects");
            }
            ObjectNode offer = (ObjectNode) offerNode;
            requiredTextField(offer, "code");
            BigDecimal offerMin = requiredNonNegativeDecimal(offer, "minAmount");
            BigDecimal offerMax = requiredNonNegativeDecimal(offer, "maxAmount");
            if (offerMax.compareTo(offerMin) < 0) {
                throw new BusinessException("financing.offers.maxAmount must be >= minAmount");
            }
            BigDecimal offerRate = optionalDecimalField(offer, "maxInterestRate");
            if (offerRate != null && (offerRate.compareTo(BigDecimal.ZERO) < 0 || offerRate.compareTo(ONE_HUNDRED) > 0)) {
                throw new BusinessException("financing.offers.maxInterestRate must be between 0 and 100");
            }
            ensureStringArray(offer, "eligibleTiers", "financing.offers.eligibleTiers must be an array of non-empty strings");
        });
    }

    private void validatePaymentConfig(ObjectNode payload) {
        // Payments are managed centrally by JawuPay. Country payloads no longer
        // carry PSP selection or payment-method routing rules.
    }

    private String requiredTextField(ObjectNode payload, String fieldName) {
        JsonNode value = payload.get(fieldName);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new BusinessException(fieldName + " is required and must be a non-empty string");
        }
        return value.asText().trim();
    }

    private String optionalTextField(ObjectNode payload, String fieldName) {
        JsonNode value = payload.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new BusinessException(fieldName + " must be a non-empty string");
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

    private BigDecimal requiredPositiveDecimal(ObjectNode payload, String fieldName) {
        JsonNode value = payload.get(fieldName);
        if (value == null || !value.isNumber()) {
            throw new BusinessException(fieldName + " is required and must be numeric");
        }
        BigDecimal decimal = value.decimalValue();
        if (decimal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(fieldName + " must be > 0");
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

    private BigDecimal optionalPercentageField(ObjectNode payload, String fieldName, String errorPrefix) {
        BigDecimal value = optionalDecimalField(payload, fieldName);
        if (value == null) {
            return null;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(ONE_HUNDRED) > 0) {
            throw new BusinessException(errorPrefix + " must be between 0 and 100");
        }
        return value;
    }

    private String resolveValidationField(String message) {
        if (message == null || message.isBlank()) {
            return "setup";
        }
        String normalizedMessage = message.trim();
        String normalizedLower = normalizedMessage.toLowerCase(Locale.ROOT);
        if (normalizedLower.startsWith("general.")) {
            return "general";
        }
        if (normalizedLower.startsWith("membership.")) {
            return "membership";
        }
        if (normalizedLower.startsWith("mlm_limits.") || normalizedLower.contains("mlm")) {
            return "mlm_limits";
        }
        if (normalizedLower.startsWith("guarantee.") || normalizedLower.contains("guarantee")) {
            return "guarantee";
        }
        if (normalizedLower.startsWith("financing.") || normalizedLower.contains("financing")) {
            return "financing";
        }
        if (normalizedLower.startsWith("accounting.") || normalizedLower.contains("accounting")) {
            return "accounting";
        }
        return "setup";
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

        validateMlmMembershipAlignment(payloadByType.get(CountryConfigType.MLM_LIMITS),
                payloadByType.get(CountryConfigType.MEMBERSHIP));
        validateGuaranteeMembershipAlignment(payloadByType.get(CountryConfigType.GUARANTEE),
                payloadByType.get(CountryConfigType.MEMBERSHIP));
        validateGuaranteeFinancingAlignment(payloadByType.get(CountryConfigType.GUARANTEE),
                payloadByType.get(CountryConfigType.FINANCING));
        validateAccountingAccountsExist(countryCode, payloadByType.get(CountryConfigType.ACCOUNTING));
    }

    private ObjectNode buildManagedPaymentPayload() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("provider", JAWUPAY_PROVIDER);
        payload.put("managedBy", JAWUPAY_PROVIDER);
        payload.put("methodAvailabilitySource", JAWUPAY_METHOD_SOURCE);
        payload.put("countryRoutingSource", JAWUPAY_PROVIDER);
        payload.put("countryOverridesAllowed", false);
        return payload;
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
        Set<String> cappedTiers = readTierSet(mlmConfig, "weeklyCapsByTier");
        if (cappedTiers.isEmpty()) {
            return;
        }

        Set<String> membershipFormulas = readMembershipPaidFormulaSet(membershipConfig);
        if (membershipFormulas.isEmpty()) {
            throw new BusinessException("membership.plans must define paid formula variants before tier-based mlm_limits");
        }
        if (!membershipFormulas.containsAll(cappedTiers) || !cappedTiers.containsAll(membershipFormulas)) {
            throw new BusinessException("MLM caps must align with membership tiers");
        }
    }

    private void validateGuaranteeMembershipAlignment(ObjectNode guaranteeConfig, ObjectNode membershipConfig) {
        Set<String> guaranteeTiers = readTierSet(guaranteeConfig, "limitsByTier");
        if (guaranteeTiers.isEmpty()) {
            return;
        }

        Set<String> membershipFormulas = readMembershipPaidFormulaSet(membershipConfig);
        if (membershipFormulas.isEmpty()) {
            throw new BusinessException("membership.plans must define paid formula variants before tier-based guarantee limits");
        }
        if (!membershipFormulas.containsAll(guaranteeTiers) || !guaranteeTiers.containsAll(membershipFormulas)) {
            throw new BusinessException("Guarantee configuration must align with membership tiers");
        }
    }

    private void validateGuaranteeFinancingAlignment(ObjectNode guaranteeConfig, ObjectNode financingConfig) {
        BigDecimal financingMaxAmount = optionalDecimalField(financingConfig, "maxAmount");
        BigDecimal offersMaxAmount = resolveMaxFinancingOfferAmount(financingConfig);
        if (financingMaxAmount == null || (offersMaxAmount != null && offersMaxAmount.compareTo(financingMaxAmount) > 0)) {
            financingMaxAmount = offersMaxAmount;
        }
        if (financingMaxAmount == null) {
            return;
        }

        BigDecimal globalMaxExposure = optionalDecimalField(guaranteeConfig, "globalMaxExposure");
        if (globalMaxExposure != null && globalMaxExposure.compareTo(financingMaxAmount) > 0) {
            throw new BusinessException("Guarantee must align with financing rules");
        }

        JsonNode limitsByTierNode = guaranteeConfig.get("limitsByTier");
        if (limitsByTierNode == null || !limitsByTierNode.isArray()) {
            return;
        }
        for (JsonNode limitNode : limitsByTierNode) {
            if (!limitNode.isObject()) {
                continue;
            }
            ObjectNode limit = (ObjectNode) limitNode;
            BigDecimal maxAmount = optionalDecimalField(limit, "maxAmount");
            if (maxAmount != null && maxAmount.compareTo(financingMaxAmount) > 0) {
                throw new BusinessException("Guarantee maxAmount must be <= financing maxAmount");
            }
            BigDecimal maxExposure = optionalDecimalField(limit, "maxExposure");
            if (maxExposure != null && maxExposure.compareTo(financingMaxAmount) > 0) {
                throw new BusinessException("Guarantee maxExposure must be <= financing maxAmount");
            }
        }
    }

    private void validateAccountingAccountsExist(String countryCode, ObjectNode accountingConfig) {
        JsonNode accountsNode = accountingConfig.has("accounts")
                ? accountingConfig.get("accounts")
                : accountingConfig.get("preallocationAccounts");
        if (accountsNode != null && accountsNode.isObject()) {
            ObjectNode accounts = (ObjectNode) accountsNode;
            if (accounts.isEmpty()) {
                return;
            }
            accounts.properties().forEach(entry -> {
                String accountCode = entry.getValue().asText("").trim().toUpperCase(Locale.ROOT);
                if (accountCode.isBlank() || !accountCode.matches("[A-Z0-9._-]{3,64}")) {
                    throw new BusinessException("accounting accounts exist and valid");
                }
                boolean exists = countryChartAccountRepository.existsByCountryCodeAndAccountCodeAndStatus(
                        countryCode,
                        accountCode,
                        CountryChartAccountStatus.ACTIVE
                );
                if (!exists) {
                    throw new BusinessException("accounting accounts exist and valid");
                }
            });
            return;
        }

        // Validate only — do NOT create accounts here (this runs inside a readOnly transaction).
        // Account generation must be done explicitly via autoGenerateRequiredAccounts.
        if (!countryChartOfAccountsService.hasRequiredSystemMappings(countryCode)) {
            throw new BusinessException("Required system accounts are not yet generated for this country. " +
                    "Use the 'Generate accounts' action in Step 2 before activating.");
        }
    }

    private void validatePaymentMethodsAgainstPspConfig(ObjectNode paymentConfig) {
        // No-op by design: JawuPay owns method availability and country routing.
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

    private Set<String> readTierSet(ObjectNode payload, String fieldName) {
        JsonNode node = payload.get(fieldName);
        Set<String> values = new HashSet<>();
        if (node == null || !node.isArray()) {
            return values;
        }
        node.forEach(item -> {
            if (!item.isObject()) {
                return;
            }
            ObjectNode entry = (ObjectNode) item;
            JsonNode tierNode = entry.get("tier");
            if (tierNode == null || !tierNode.isTextual() || tierNode.asText().isBlank()) {
                throw new BusinessException(fieldName + ".tier is required");
            }
            values.add(tierNode.asText().trim().toUpperCase(Locale.ROOT));
        });
        return values;
    }

    private Set<String> readMembershipPaidFormulaSet(ObjectNode membershipConfig) {
        JsonNode plans = membershipConfig.get("plans");
        Set<String> tiers = new HashSet<>();
        if (plans == null || !plans.isArray()) {
            return tiers;
        }
        plans.forEach(planNode -> {
            if (!planNode.isObject()) {
                return;
            }
            ObjectNode plan = (ObjectNode) planNode;
            String formulaType = optionalTextField(plan, "formulaType");
            String tier = optionalTextField(plan, "tier");
            String code = optionalTextField(plan, "code");
            String name = optionalTextField(plan, "name");
            String selected = formulaType != null ? formulaType : (tier != null ? tier : (code != null ? code : name));
            if (selected != null && !selected.isBlank()) {
                String normalized = selected.trim().toUpperCase(Locale.ROOT);
                if (!"FREE".equals(normalized)) {
                    tiers.add(normalized);
                }
            }
        });
        return tiers;
    }

    private BigDecimal resolveMaxFinancingOfferAmount(ObjectNode financingConfig) {
        JsonNode offersNode = financingConfig.get("offers");
        if (offersNode == null || !offersNode.isArray()) {
            return null;
        }
        BigDecimal max = null;
        for (JsonNode offerNode : offersNode) {
            if (!offerNode.isObject()) {
                continue;
            }
            JsonNode maxAmountNode = offerNode.get("maxAmount");
            if (maxAmountNode == null || !maxAmountNode.isNumber()) {
                continue;
            }
            BigDecimal candidate = maxAmountNode.decimalValue();
            if (max == null || candidate.compareTo(max) > 0) {
                max = candidate;
            }
        }
        return max;
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
        synchronizeCountryConfiguredState(countryCode, true);
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

    private record SetupValidationResult(
            boolean valid,
            List<CountryConfigValidationErrorResponse> errors
    ) {
    }
}
