package com.ccapp.country.config.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum CountryConfigType {
    ACCOUNTING_CONFIG("accounting_config"),
    MLM_CONFIG("mlm_config"),
    MEMBERSHIP_CONFIG("membership_config"),
    FINANCING_CONFIG("financing_config"),
    PAYMENT_CONFIG("payment_config"),
    CIRCLE_CONFIG("circle_config"),
    RISK_CONFIG("risk_config"),
    KYC_CONFIG("kyc_config"),
    FEATURE_FLAGS("feature_flags");

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
        for (CountryConfigType type : values()) {
            if (type.externalName.equals(normalized)
                    || type.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Unsupported configType: " + rawValue);
    }
}
