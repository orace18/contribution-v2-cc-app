package com.ccapp.platform.config.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum PlatformConfigType {
    GLOBAL_GENERAL("global_general"),
    GLOBAL_MLM_BASE_RATES("global_mlm_base_rates"),
    GLOBAL_ALLOCATION("global_allocation");

    private final String externalName;

    PlatformConfigType(String externalName) {
        this.externalName = externalName;
    }

    @JsonValue
    public String externalName() {
        return externalName;
    }

    @JsonCreator
    public static PlatformConfigType fromExternalName(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("configType is required");
        }

        String normalized = rawValue.trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_');
        normalized = switch (normalized) {
            case "global_mlm_fallback" -> "global_mlm_base_rates";
            case "global_feature_flags", "system_limits" -> "global_general";
            default -> normalized;
        };

        for (PlatformConfigType type : values()) {
            if (type.externalName.equals(normalized)
                    || type.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Unsupported platform configType: " + rawValue);
    }
}
