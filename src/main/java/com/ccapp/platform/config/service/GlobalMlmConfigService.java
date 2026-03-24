package com.ccapp.platform.config.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.service.CountryConfigResolver;
import com.ccapp.platform.config.domain.enums.PlatformConfigType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GlobalMlmConfigService {

    public static final int MAX_GENERATIONS = 10;
    public static final String DEFAULT_BINARY_MATCHING_RULE = "WEAKER_LEG";
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final CountryConfigResolver countryConfigResolver;

    @Transactional(readOnly = true)
    public GlobalMlmConfig resolveActiveConfig() {
        ObjectNode payload = countryConfigResolver.payloadAsObjectNode(
                countryConfigResolver.resolveActivePlatformConfig(PlatformConfigType.GLOBAL_MLM_BASE_RATES)
        );
        return parsePayload(payload, true);
    }

    public void validateStrictPayload(ObjectNode payload) {
        parsePayload(payload, false);
    }

    public boolean isResolvable(ObjectNode payload) {
        try {
            parsePayload(payload, true);
            return true;
        } catch (BusinessException ex) {
            return false;
        }
    }

    private GlobalMlmConfig parsePayload(ObjectNode payload, boolean allowLegacyFallback) {
        if (payload == null || payload.isEmpty()) {
            throw new BusinessException("global_mlm_base_rates payload is required");
        }

        return new GlobalMlmConfig(
                parseUnilevelRates(payload, allowLegacyFallback),
                parseBinaryConfig(payload, allowLegacyFallback)
        );
    }

    private Map<Integer, BigDecimal> parseUnilevelRates(ObjectNode payload, boolean allowLegacyFallback) {
        JsonNode unilevelRatesNode = payload.get("unilevelRates");
        if (unilevelRatesNode == null || unilevelRatesNode.isNull()) {
            if (!allowLegacyFallback) {
                throw new BusinessException("global_mlm_base_rates.unilevelRates is required");
            }
            if (!hasLegacyUnilevelRates(payload)) {
                throw new BusinessException("global_mlm_base_rates.unilevelRates is required");
            }
        } else if (!unilevelRatesNode.isObject()) {
            throw new BusinessException("global_mlm_base_rates.unilevelRates must be an object");
        }

        Map<Integer, BigDecimal> ratesByGeneration = new LinkedHashMap<>();
        for (int generation = 1; generation <= MAX_GENERATIONS; generation++) {
            JsonNode rateNode = resolveGenerationRateNode(payload, generation, allowLegacyFallback);
            if (rateNode == null || !rateNode.isNumber()) {
                throw new BusinessException("global_mlm_base_rates.unilevelRates.g" + generation + " must be numeric");
            }
            BigDecimal rate = rateNode.decimalValue();
            validatePercentage(rate, "global_mlm_base_rates.unilevelRates.g" + generation);
            ratesByGeneration.put(generation, rate);
        }
        return ratesByGeneration;
    }

    private boolean hasLegacyUnilevelRates(ObjectNode payload) {
        if (payload.has("unilevel") && payload.get("unilevel").isNumber()) {
            return true;
        }
        if (payload.has("generations") && payload.get("generations").isObject()) {
            return true;
        }
        for (int generation = 1; generation <= MAX_GENERATIONS; generation++) {
            if (payload.has("g" + generation) && payload.get("g" + generation).isNumber()) {
                return true;
            }
        }
        return false;
    }

    private JsonNode resolveGenerationRateNode(ObjectNode payload, int generation, boolean allowLegacyFallback) {
        JsonNode unilevelRates = payload.get("unilevelRates");
        if (unilevelRates != null && unilevelRates.isObject()) {
            JsonNode configured = unilevelRates.get("g" + generation);
            if (configured != null) {
                return configured;
            }
        }

        if (!allowLegacyFallback) {
            return null;
        }

        JsonNode generations = payload.get("generations");
        if (generations != null && generations.isObject()) {
            JsonNode configured = generations.get("g" + generation);
            if (configured != null) {
                return configured;
            }
        }

        JsonNode topLevel = payload.get("g" + generation);
        if (topLevel != null) {
            return topLevel;
        }

        JsonNode flatUnilevel = payload.get("unilevel");
        if (flatUnilevel != null && flatUnilevel.isNumber()) {
            return flatUnilevel;
        }

        return null;
    }

    private GlobalBinaryConfig parseBinaryConfig(ObjectNode payload, boolean allowLegacyFallback) {
        JsonNode binaryConfigNode = payload.get("binaryConfig");
        ObjectNode binaryConfigPayload = null;
        if (binaryConfigNode != null && !binaryConfigNode.isNull()) {
            if (!binaryConfigNode.isObject()) {
                throw new BusinessException("global_mlm_base_rates.binaryConfig must be an object");
            }
            binaryConfigPayload = (ObjectNode) binaryConfigNode;
        }

        String matchingRule;
        BigDecimal percentage;
        if (binaryConfigPayload != null) {
            JsonNode matchingRuleNode = binaryConfigPayload.get("matchingRule");
            if (matchingRuleNode == null || !matchingRuleNode.isTextual() || matchingRuleNode.asText().isBlank()) {
                throw new BusinessException("global_mlm_base_rates.binaryConfig.matchingRule is required");
            }
            matchingRule = matchingRuleNode.asText().trim().toUpperCase(Locale.ROOT);

            JsonNode percentageNode = binaryConfigPayload.get("percentage");
            if (percentageNode == null || !percentageNode.isNumber()) {
                throw new BusinessException("global_mlm_base_rates.binaryConfig.percentage is required and must be numeric");
            }
            percentage = percentageNode.decimalValue();
        } else if (allowLegacyFallback && payload.has("binary") && payload.get("binary").isNumber()) {
            matchingRule = DEFAULT_BINARY_MATCHING_RULE;
            percentage = payload.get("binary").decimalValue();
        } else {
            throw new BusinessException("global_mlm_base_rates.binaryConfig is required");
        }

        if (!DEFAULT_BINARY_MATCHING_RULE.equals(matchingRule)) {
            throw new BusinessException("global_mlm_base_rates.binaryConfig.matchingRule must be WEAKER_LEG");
        }
        validatePercentage(percentage, "global_mlm_base_rates.binaryConfig.percentage");
        return new GlobalBinaryConfig(matchingRule, percentage);
    }

    private void validatePercentage(BigDecimal value, String errorPrefix) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(ONE_HUNDRED) > 0) {
            throw new BusinessException(errorPrefix + " must be between 0 and 100");
        }
    }

    public record GlobalBinaryConfig(
            String matchingRule,
            BigDecimal percentage
    ) {
    }

    public record GlobalMlmConfig(
            Map<Integer, BigDecimal> unilevelRates,
            GlobalBinaryConfig binaryConfig
    ) {
        public BigDecimal generationRate(int generation) {
            return unilevelRates.get(generation);
        }
    }
}
