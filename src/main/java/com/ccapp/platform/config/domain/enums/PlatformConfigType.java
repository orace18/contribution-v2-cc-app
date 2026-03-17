package com.ccapp.platform.config.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum PlatformConfigType {
    GLOBAL_MLM_FALLBACK("global_mlm_fallback"),
    GLOBAL_FEATURE_FLAGS("global_feature_flags"),
    SYSTEM_LIMITS("system_limits");

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

        for (PlatformConfigType type : values()) {
            if (type.externalName.equals(normalized)
                    || type.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Unsupported platform configType: " + rawValue);
    }
}
