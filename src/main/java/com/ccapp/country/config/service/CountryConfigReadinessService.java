package com.ccapp.country.config.service;

import com.ccapp.admin.domain.enums.AdminRole;
import com.ccapp.admin.domain.enums.AdminUserStatus;
import com.ccapp.admin.repository.AdminUserRepository;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.domain.entity.CountryConfigVersion;
import com.ccapp.country.config.domain.enums.CountryAccountSystemTag;
import com.ccapp.country.config.domain.enums.CountryChartAccountStatus;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.dto.CountryConfigReadinessResponse;
import com.ccapp.country.config.dto.CountryConfigReadinessSectionResponse;
import com.ccapp.country.config.dto.CountryConfigReadinessStatus;
import com.ccapp.country.config.repository.CountryChartAccountRepository;
import com.ccapp.country.config.repository.CountryAccountSystemMappingRepository;
import com.ccapp.country.config.repository.CountryConfigVersionRepository;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.repository.CountryRepository;
import com.ccapp.financing.repository.OfferCountryConfigRepository;
import com.ccapp.platform.config.domain.enums.PlatformConfigType;
import com.ccapp.platform.config.service.GlobalMlmConfigService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CountryConfigReadinessService {

    private static final Set<Integer> REQUIRED_PAID_DURATIONS = Set.of(1, 3, 12);
    private static final Set<String> REQUIRED_PAID_FORMULAS = Set.of("CLASSIC", "GOLD", "DIAMOND");

    private final CountryRepository countryRepository;
    private final CountryConfigVersionRepository countryConfigVersionRepository;
    private final CountryChartAccountRepository countryChartAccountRepository;
    private final CountryAccountSystemMappingRepository countryAccountSystemMappingRepository;
    private final OfferCountryConfigRepository offerCountryConfigRepository;
    private final AdminUserRepository adminUserRepository;
    private final CountryConfigResolver countryConfigResolver;
    private final GlobalMlmConfigService globalMlmConfigService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public CountryConfigReadinessResponse evaluateReadiness(String countryCode) {
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        Country country = countryRepository.findByCode(normalizedCountryCode).orElse(null);
        Map<CountryConfigType, ObjectNode> payloadByType = resolveActivePayloads(normalizedCountryCode);

        // Configuration sections — these determine whether the country is "configured".
        // All 6 must pass for configured = true.
        List<SectionAccumulator> configSections = new ArrayList<>();
        configSections.add(evaluateGeneralSection(country));
        configSections.add(evaluateAccountingSection(normalizedCountryCode, payloadByType.get(CountryConfigType.ACCOUNTING)));
        configSections.add(evaluateMembershipSection(payloadByType.get(CountryConfigType.MEMBERSHIP), payloadByType.get(CountryConfigType.MLM_LIMITS)));
        configSections.add(evaluateMlmSection(normalizedCountryCode, payloadByType.get(CountryConfigType.MLM_LIMITS)));
        configSections.add(evaluateFinancingSection(normalizedCountryCode, payloadByType.get(CountryConfigType.FINANCING)));
        configSections.add(evaluateRiskSection(payloadByType.get(CountryConfigType.GUARANTEE)));

        // Operational sections — affect score and readiness status but do NOT block
        // country.configured = true. RBAC assignment is a separate operational concern.
        List<SectionAccumulator> operationalSections = new ArrayList<>();
        operationalSections.add(evaluateRbacSection(country));

        List<SectionAccumulator> allSections = new ArrayList<>(configSections);
        allSections.addAll(operationalSections);

        int totalChecks = allSections.stream().mapToInt(SectionAccumulator::totalChecks).sum();
        int missingChecks = allSections.stream().mapToInt(section -> section.missing().size()).sum();
        int passedChecks = Math.max(totalChecks - missingChecks, 0);
        int score = totalChecks == 0 ? 0 : (int) Math.round((passedChecks * 100.0d) / totalChecks);

        List<String> missingItems = allSections.stream()
                .flatMap(section -> section.missing().stream())
                .toList();

        // configured = all configuration sections pass (RBAC does not block this).
        List<String> configMissingItems = configSections.stream()
                .flatMap(section -> section.missing().stream())
                .toList();
        boolean configured = configMissingItems.isEmpty() && country != null;

        CountryConfigReadinessStatus status;
        if (configured && missingItems.isEmpty()) {
            status = CountryConfigReadinessStatus.READY;
        } else if (configured) {
            // All 6 config sections passed (wizard complete) but operational checks (e.g. RBAC) pending.
            status = CountryConfigReadinessStatus.CONFIGURED;
        } else if (score == 0) {
            status = CountryConfigReadinessStatus.DRAFT;
        } else {
            status = CountryConfigReadinessStatus.IN_PROGRESS;
        }

        List<CountryConfigReadinessSectionResponse> sectionResponses = allSections.stream()
                .map(section -> new CountryConfigReadinessSectionResponse(
                        section.section(),
                        Math.max(section.totalChecks() - section.missing().size(), 0),
                        section.totalChecks(),
                        section.missing()
                ))
                .toList();

        return new CountryConfigReadinessResponse(configured, score, status, missingItems, sectionResponses);
    }

    @Transactional
    public CountryConfigReadinessResponse refreshCountryConfiguredStatus(String countryCode) {
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        Country country = countryRepository.findByCode(normalizedCountryCode)
                .orElseThrow(() -> new BusinessException("Country not found"));

        CountryConfigReadinessResponse readiness = evaluateReadiness(normalizedCountryCode);
        if (country.isConfigured() != readiness.configured()) {
            country.setConfigured(readiness.configured());
            countryRepository.save(country);
        }
        return readiness;
    }

    private SectionAccumulator evaluateGeneralSection(Country country) {
        SectionAccumulator section = new SectionAccumulator("general", 2);
        if (country == null) {
            section.miss("general.country_exists");
            section.miss("general.currency");
            return section;
        }
        if (country.getCurrency() == null || country.getCurrency().isBlank() || country.getCurrency().trim().length() != 3) {
            section.miss("general.currency");
        }
        return section;
    }

    private SectionAccumulator evaluateAccountingSection(String countryCode, ObjectNode accounting) {
        SectionAccumulator section = new SectionAccumulator("accounting", 3);
        long chartCount = countryChartAccountRepository.countByCountryCodeAndStatus(countryCode, CountryChartAccountStatus.ACTIVE);
        if (chartCount == 0) {
            section.miss("accounting.chart_of_accounts");
        }

        boolean automationConfigured = (accounting != null && !accounting.isEmpty())
                || resolveAccountsNode(accounting) != null;
        if (!automationConfigured) {
            section.miss("accounting.automation_mode");
        }

        boolean hasAllSystemMappings = CountryAccountSystemTag.requiredTags().stream()
                .allMatch(tag -> countryAccountSystemMappingRepository.findByCountryCodeAndSystemTag(countryCode, tag)
                        .flatMap(mapping -> countryChartAccountRepository.findByIdAndCountryCode(mapping.getAccountId(), countryCode))
                        .isPresent());
        if (!hasAllSystemMappings) {
            section.miss("accounting.system_accounts");
        }
        return section;
    }

    private void validateAccountMapping(SectionAccumulator section,
                                        String countryCode,
                                        ObjectNode accounts,
                                        String path,
                                        String... aliases) {
        String mappedCode = findFirstString(accounts, aliases);
        if (mappedCode == null) {
            section.miss(path);
            return;
        }
        boolean exists = countryChartAccountRepository.existsByCountryCodeAndAccountCodeAndStatus(
                countryCode,
                mappedCode.trim().toUpperCase(Locale.ROOT),
                CountryChartAccountStatus.ACTIVE
        );
        if (!exists) {
            section.miss(path);
        }
    }

    private SectionAccumulator evaluateMembershipSection(ObjectNode membership, ObjectNode mlmLimits) {
        SectionAccumulator section = new SectionAccumulator("membership", 6);
        JsonNode plansNode = membership.get("plans");
        if (plansNode == null || !plansNode.isArray() || plansNode.isEmpty()) {
            section.miss("membership.classic_variants");
            section.miss("membership.gold_variants");
            section.miss("membership.diamond_variants");
            section.miss("membership.free_plan");
            section.miss("membership.pricing");
            section.miss("membership.weekly_caps");
            return section;
        }

        Map<String, Set<Integer>> durationsByFormula = new java.util.HashMap<>();
        REQUIRED_PAID_FORMULAS.forEach(formula -> durationsByFormula.put(formula, new HashSet<>()));
        int freePlanCount = 0;
        boolean pricingValid = true;
        for (JsonNode planNode : plansNode) {
            if (!planNode.isObject()) {
                pricingValid = false;
                continue;
            }
            ObjectNode plan = (ObjectNode) planNode;
            String formulaType = firstNonBlank(
                    optionalText(plan, "formulaType"),
                    optionalText(plan, "tier"),
                    optionalText(plan, "code"),
                    optionalText(plan, "name")
            );
            if (formulaType == null) {
                pricingValid = false;
                continue;
            }

            String normalizedFormula = formulaType.trim().toUpperCase(Locale.ROOT);
            Integer durationMonths = optionalInt(plan, "durationMonths");
            BigDecimal totalPrice = resolveMembershipPlanTotalPrice(plan);
            String currency = resolveMembershipPlanCurrency(plan);
            boolean active = optionalBoolean(plan, "isActive", "active").orElse(true);

            if (!active || durationMonths == null || durationMonths <= 0 || currency == null) {
                pricingValid = false;
                continue;
            }

            if ("FREE".equals(normalizedFormula)) {
                freePlanCount += 1;
                if (durationMonths != 1 || totalPrice == null || totalPrice.compareTo(BigDecimal.ZERO) != 0) {
                    pricingValid = false;
                }
                continue;
            }

            if (!REQUIRED_PAID_FORMULAS.contains(normalizedFormula)) {
                pricingValid = false;
                continue;
            }

            durationsByFormula.get(normalizedFormula).add(durationMonths);
            if (totalPrice == null || totalPrice.compareTo(BigDecimal.ZERO) <= 0 || !REQUIRED_PAID_DURATIONS.contains(durationMonths)) {
                pricingValid = false;
            }
        }

        if (!durationsByFormula.get("CLASSIC").equals(REQUIRED_PAID_DURATIONS)) {
            section.miss("membership.classic_variants");
        }
        if (!durationsByFormula.get("GOLD").equals(REQUIRED_PAID_DURATIONS)) {
            section.miss("membership.gold_variants");
        }
        if (!durationsByFormula.get("DIAMOND").equals(REQUIRED_PAID_DURATIONS)) {
            section.miss("membership.diamond_variants");
        }
        if (freePlanCount != 1) {
            section.miss("membership.free_plan");
        }
        if (!pricingValid) {
            section.miss("membership.pricing");
        }

        JsonNode weeklyCapsByTier = mlmLimits.get("weeklyCapsByTier");
        if (weeklyCapsByTier == null || !weeklyCapsByTier.isArray() || weeklyCapsByTier.isEmpty()) {
            section.miss("membership.weekly_caps");
            return section;
        }
        Set<String> cappedTiers = new HashSet<>();
        boolean validCapValues = true;
        for (JsonNode capNode : weeklyCapsByTier) {
            if (!capNode.isObject()) {
                validCapValues = false;
                continue;
            }
            JsonNode tierNode = capNode.get("tier");
            JsonNode valueNode = capNode.get("cap");
            if (tierNode == null || !tierNode.isTextual() || tierNode.asText().isBlank()) {
                validCapValues = false;
                continue;
            }
            cappedTiers.add(tierNode.asText().trim().toUpperCase(Locale.ROOT));
            if (valueNode == null || !valueNode.isNumber() || valueNode.decimalValue().compareTo(BigDecimal.ZERO) < 0) {
                validCapValues = false;
            }
        }
        if (!validCapValues || !cappedTiers.containsAll(Set.of("CLASSIC", "GOLD", "DIAMOND"))) {
            section.miss("membership.weekly_caps");
        }
        return section;
    }

    private Integer optionalInt(ObjectNode payload, String fieldName) {
        if (payload == null) {
            return null;
        }
        JsonNode node = payload.get(fieldName);
        if (node == null || node.isNull() || !node.isInt()) {
            return null;
        }
        return node.intValue();
    }

    private BigDecimal resolveMembershipPlanTotalPrice(ObjectNode plan) {
        JsonNode totalPriceNode = plan.get("totalPrice");
        if (totalPriceNode != null && totalPriceNode.isNumber()) {
            return totalPriceNode.decimalValue();
        }
        JsonNode pricingNode = plan.get("pricing");
        if (pricingNode != null && pricingNode.isObject()) {
            JsonNode amountNode = pricingNode.get("amount");
            if (amountNode != null && amountNode.isNumber()) {
                return amountNode.decimalValue();
            }
        }
        return null;
    }

    private String resolveMembershipPlanCurrency(ObjectNode plan) {
        String currency = optionalText(plan, "currency");
        if (currency != null) {
            return currency;
        }
        JsonNode pricingNode = plan.get("pricing");
        if (pricingNode != null && pricingNode.isObject()) {
            JsonNode currencyNode = pricingNode.get("currency");
            if (currencyNode != null && currencyNode.isTextual() && !currencyNode.asText().isBlank()) {
                return currencyNode.asText().trim();
            }
        }
        return null;
    }

    private SectionAccumulator evaluateMlmSection(String countryCode, ObjectNode mlmLimitsCountryPayload) {
        SectionAccumulator section = new SectionAccumulator("mlm", 2);
        ObjectNode globalMlm = countryConfigResolver.payloadAsObjectNode(
                countryConfigResolver.resolveActivePlatformConfig(PlatformConfigType.GLOBAL_MLM_BASE_RATES)
        );

        if (!globalMlmConfigService.isResolvable(globalMlm)) {
            section.miss("mlm.commission_rates");
        }

        boolean hasLimits = (readDecimal(mlmLimitsCountryPayload, "weeklyCap") != null)
                || (mlmLimitsCountryPayload.has("weeklyCapsByTier")
                && mlmLimitsCountryPayload.get("weeklyCapsByTier").isArray()
                && !mlmLimitsCountryPayload.get("weeklyCapsByTier").isEmpty());
        if (!hasLimits) {
            section.miss("mlm.limits");
        }
        return section;
    }

    private SectionAccumulator evaluateFinancingSection(String countryCode, ObjectNode financing) {
        SectionAccumulator section = new SectionAccumulator("financing", 2);
        boolean offersInPayload = financing.has("offers")
                && financing.get("offers").isArray()
                && !financing.get("offers").isEmpty();
        boolean hasEnabledOfferConfig = !offerCountryConfigRepository
                .findByCountryCodeAndEnabledTrueOrderByCreatedAtDesc(countryCode)
                .isEmpty();
        boolean financingEnabled = optionalBoolean(financing, "enabled", "financingEnabled")
                .orElse(offersInPayload || hasEnabledOfferConfig);

        if (financingEnabled && !offersInPayload && !hasEnabledOfferConfig) {
            section.miss("financing.active_offer");
        }

        boolean seedRequired = optionalBoolean(financing, "seedCapitalRequired").orElse(false);
        if (seedRequired) {
            String seedOfferId = firstNonBlank(
                    optionalText(financing, "seedCapitalOfferId"),
                    optionalText(financing, "seedCapitalOfferCode")
            );
            if (seedOfferId == null) {
                section.miss("financing.seed_capital_offer");
            }
        }
        return section;
    }

    private SectionAccumulator evaluateRiskSection(ObjectNode guarantee) {
        SectionAccumulator section = new SectionAccumulator("risk_guarantee", 2);
        BigDecimal globalMaxExposure = readDecimal(guarantee, "globalMaxExposure");
        JsonNode limitsByTier = guarantee.get("limitsByTier");
        boolean hasCoverage = (globalMaxExposure != null && globalMaxExposure.compareTo(BigDecimal.ZERO) > 0)
                || (limitsByTier != null && limitsByTier.isArray() && !limitsByTier.isEmpty());
        if (!hasCoverage) {
            section.miss("risk.guarantee_coverage");
        }

        JsonNode riskLimitsNode = guarantee.get("riskLimits");
        boolean hasRiskLimits = false;
        if (riskLimitsNode != null && riskLimitsNode.isObject()) {
            hasRiskLimits = riskLimitsNode.fields().hasNext();
        }
        if (!hasRiskLimits && limitsByTier != null && limitsByTier.isArray()) {
            for (JsonNode item : limitsByTier) {
                if (item.isObject()) {
                    JsonNode maxExposure = item.get("maxExposure");
                    if (maxExposure != null && maxExposure.isNumber()) {
                        hasRiskLimits = true;
                        break;
                    }
                }
            }
        }
        if (!hasRiskLimits) {
            section.miss("risk.risk_limits");
        }
        return section;
    }

    private SectionAccumulator evaluateRbacSection(Country country) {
        SectionAccumulator section = new SectionAccumulator("rbac", 1);
        if (country == null) {
            section.miss("rbac.country_ops");
            return section;
        }
        boolean hasCountryOps = adminUserRepository.existsByCountryIdAndStatusAndRoleIn(
                country.getId(),
                AdminUserStatus.ACTIVE,
                List.of(AdminRole.ROLE_COUNTRY_ADMIN, AdminRole.ROLE_OPERATIONS)
        );
        if (!hasCountryOps) {
            section.miss("rbac.country_ops");
        }
        return section;
    }

    private Map<CountryConfigType, ObjectNode> resolveActivePayloads(String countryCode) {
        Map<CountryConfigType, ObjectNode> payloadByType = new EnumMap<>(CountryConfigType.class);
        for (CountryConfigType type : CountryConfigType.values()) {
            payloadByType.put(type, countryConfigVersionRepository
                    .findFirstByCountryCodeAndConfigTypeAndEffectiveDateLessThanEqualOrderByEffectiveDateDescVersionDesc(
                            countryCode,
                            type,
                            OffsetDateTime.now()
                    )
                    .map(CountryConfigVersion::getPayload)
                    .map(this::readObjectPayload)
                    .orElseGet(objectMapper::createObjectNode));
        }
        return payloadByType;
    }

    private ObjectNode resolveAccountsNode(ObjectNode accounting) {
        if (accounting == null) {
            return null;
        }
        JsonNode accountsNode = accounting.get("accounts");
        if (accountsNode != null && accountsNode.isObject()) {
            return (ObjectNode) accountsNode;
        }
        JsonNode preallocationNode = accounting.get("preallocationAccounts");
        if (preallocationNode != null && preallocationNode.isObject()) {
            return (ObjectNode) preallocationNode;
        }
        return null;
    }

    private String findFirstString(ObjectNode payload, String... aliases) {
        if (payload == null) {
            return null;
        }
        for (String alias : aliases) {
            JsonNode node = payload.get(alias);
            if (node != null && node.isTextual() && !node.asText().isBlank()) {
                return node.asText().trim();
            }
        }
        return null;
    }

    private ObjectNode readObjectPayload(String rawPayload) {
        try {
            JsonNode parsed = objectMapper.readTree(rawPayload);
            if (parsed != null && parsed.isObject()) {
                return (ObjectNode) parsed;
            }
        } catch (JsonProcessingException ignored) {
        }
        return objectMapper.createObjectNode();
    }

    private String optionalText(ObjectNode payload, String field) {
        if (payload == null) {
            return null;
        }
        JsonNode node = payload.get(field);
        if (node == null || node.isNull() || !node.isTextual() || node.asText().isBlank()) {
            return null;
        }
        return node.asText().trim();
    }

    private BigDecimal readDecimal(ObjectNode payload, String field) {
        if (payload == null) {
            return null;
        }
        JsonNode node = payload.get(field);
        if (node == null || node.isNull() || !node.isNumber()) {
            return null;
        }
        return node.decimalValue();
    }

    private boolean isPercentage(BigDecimal value) {
        return value != null
                && value.compareTo(BigDecimal.ZERO) >= 0
                && value.compareTo(new BigDecimal("100")) <= 0;
    }

    private java.util.Optional<Boolean> optionalBoolean(ObjectNode payload, String... fields) {
        if (payload == null) {
            return java.util.Optional.empty();
        }
        for (String field : fields) {
            JsonNode node = payload.get(field);
            if (node != null && node.isBoolean()) {
                return java.util.Optional.of(node.booleanValue());
            }
        }
        return java.util.Optional.empty();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String normalizeCountryCode(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            throw new BusinessException("Country code is required");
        }
        return countryCode.trim().toUpperCase(Locale.ROOT);
    }

    private static final class SectionAccumulator {
        private final String section;
        private final int totalChecks;
        private final List<String> missing = new ArrayList<>();

        private SectionAccumulator(String section, int totalChecks) {
            this.section = section;
            this.totalChecks = totalChecks;
        }

        private void miss(String key) {
            missing.add(key);
        }

        private String section() {
            return section;
        }

        private int totalChecks() {
            return totalChecks;
        }

        private List<String> missing() {
            return missing;
        }
    }
}
