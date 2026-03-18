package com.ccapp.country.config.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum CountryConfigType {
    GENERAL("general"),
    MEMBERSHIP("membership"),
    MLM_LIMITS("mlm_limits"),
    GUARANTEE("guarantee"),
    FINANCING("financing"),
    ACCOUNTING("accounting"),
    PAYMENT("payment");

    private final String externalName;

    CountryConfigType(String externalName) {
        this.externalName = externalName;
    }

    @JsonValue
    public String externalName() {
        return externalName;
    }

    @JsonCreator
    public static CountryConfigType fromExternalName(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("configType is required");
        }

        String normalized = rawValue.trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_');
        normalized = switch (normalized) {
            case "accounting_config" -> "accounting";
            case "mlm_config" -> "mlm_limits";
            case "membership_config" -> "membership";
            case "financing_config" -> "financing";
            case "payment_config" -> "payment";
            case "circle_config", "feature_flags", "kyc_config" -> "general";
            case "risk_config" -> "guarantee";
            default -> normalized;
        };
        for (CountryConfigType type : values()) {
            if (type.externalName.equals(normalized)
                    || type.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Unsupported configType: " + rawValue);
    }
}
