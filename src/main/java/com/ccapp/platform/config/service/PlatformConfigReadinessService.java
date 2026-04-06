package com.ccapp.platform.config.service;

import com.ccapp.platform.config.domain.enums.PlatformConfigType;
import com.ccapp.platform.config.dto.PlatformConfigReadinessResponse;
import com.ccapp.platform.config.dto.PlatformConfigSnapshotItemResponse;
import com.ccapp.platform.config.dto.PlatformConfigSnapshotResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlatformConfigReadinessService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final List<AllocationField> REQUIRED_SUBSCRIPTION_ALLOCATION_FIELDS = List.of(
            new AllocationField("commissionPool", new String[]{"commissionPool", "commission_pool"}),
            new AllocationField("guaranteeFund", new String[]{"guaranteeFund", "guarantee_fund"}),
            new AllocationField("platformFee", new String[]{"platformFee", "platform_fee"}),
            new AllocationField("administration", new String[]{"administration", "administrationFee"}),
            new AllocationField("socialFund", new String[]{"socialFund", "social_fund"})
    );

    private final PlatformConfigurationService platformConfigurationService;
    private final GlobalMlmConfigService globalMlmConfigService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PlatformConfigReadinessResponse evaluateReadiness(OffsetDateTime asOf) {
        PlatformConfigSnapshotResponse snapshot = platformConfigurationService.getPlatformConfiguration(asOf);
        ObjectNode general = payload(snapshot, PlatformConfigType.GLOBAL_GENERAL);
        ObjectNode mlm = payload(snapshot, PlatformConfigType.GLOBAL_MLM_BASE_RATES);
        ObjectNode allocation = payload(snapshot, PlatformConfigType.GLOBAL_ALLOCATION);

        List<String> missing = new ArrayList<>();
        if (!isGlobalMlmValid(mlm)) {
            missing.add("platform.global_mlm_config");
        }
        if (!isAllocationValid(allocation)) {
            missing.add("platform.allocation");
        }
        if (!hasSystemLimits(general)) {
            missing.add("platform.system_limits");
        }
        if (!hasValidFeatureFlags(general)) {
            missing.add("platform.feature_flags");
        }

        int totalChecks = 4;
        int passedChecks = totalChecks - missing.size();
        int score = (int) Math.round((passedChecks * 100.0d) / totalChecks);
        return new PlatformConfigReadinessResponse(missing.isEmpty(), score, missing);
    }

    private ObjectNode payload(PlatformConfigSnapshotResponse snapshot, PlatformConfigType configType) {
        return snapshot.configs().stream()
                .filter(item -> item.configType() == configType)
                .map(PlatformConfigSnapshotItemResponse::payload)
                .filter(payload -> payload != null && payload.isObject())
                .map(payload -> (ObjectNode) payload)
                .findFirst()
                .orElseGet(objectMapper::createObjectNode);
    }

    private boolean isGlobalMlmValid(ObjectNode mlm) {
        return globalMlmConfigService.isResolvable(mlm);
    }

    private boolean isAllocationValid(ObjectNode allocationPayload) {
        JsonNode percentagesNode = allocationPayload.has("percentages")
                ? allocationPayload.get("percentages")
                : allocationPayload;
        if (percentagesNode == null || !percentagesNode.isObject()) {
            return false;
        }
        ObjectNode percentages = (ObjectNode) percentagesNode;
        if (percentages.isEmpty()) {
            return false;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (AllocationField field : REQUIRED_SUBSCRIPTION_ALLOCATION_FIELDS) {
            BigDecimal value = readPercentage(percentages, field);
            if (value == null) {
                return false;
            }
            if (!isPercentage(value)) {
                return false;
            }
            total = total.add(value);
        }
        return total.setScale(4, RoundingMode.HALF_UP).compareTo(ONE_HUNDRED.setScale(4, RoundingMode.HALF_UP)) == 0;
    }

    private BigDecimal readPercentage(ObjectNode percentages, AllocationField field) {
        for (String alias : field.aliases()) {
            JsonNode value = percentages.get(alias);
            if (value == null || value.isNull()) {
                continue;
            }
            if (!value.isNumber()) {
                return null;
            }
            return value.decimalValue();
        }
        return null;
    }

    private boolean hasSystemLimits(ObjectNode general) {
        JsonNode limitsNode = general.get("systemLimits");
        if (limitsNode != null && limitsNode.isObject() && limitsNode.fields().hasNext()) {
            return true;
        }
        return hasNumericField(general, "minMembers")
                || hasNumericField(general, "maxMembers")
                || hasNumericField(general, "maxAssistedMembers")
                || (general.has("circle")
                && general.get("circle").isObject()
                && hasNumericField((ObjectNode) general.get("circle"), "minMembers")
                && hasNumericField((ObjectNode) general.get("circle"), "maxMembers"));
    }

    private boolean hasValidFeatureFlags(ObjectNode general) {
        JsonNode featureFlagsNode = general.has("featureFlags")
                ? general.get("featureFlags")
                : general.get("features");
        if (featureFlagsNode == null || !featureFlagsNode.isObject()) {
            return false;
        }
        ObjectNode flags = (ObjectNode) featureFlagsNode;
        if (flags.isEmpty()) {
            return false;
        }
        for (var fields = flags.fields(); fields.hasNext(); ) {
            JsonNode value = fields.next().getValue();
            if (!value.isBoolean()) {
                return false;
            }
        }
        return true;
    }

    private boolean hasNumericField(ObjectNode payload, String fieldName) {
        JsonNode value = payload.get(fieldName);
        return value != null && value.isNumber();
    }

    private boolean isPercentage(BigDecimal value) {
        if (value == null) {
            return false;
        }
        return value.compareTo(BigDecimal.ZERO) >= 0 && value.compareTo(ONE_HUNDRED) <= 0;
    }

    private record AllocationField(String label, String[] aliases) {
    }
}
