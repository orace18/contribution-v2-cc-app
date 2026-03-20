package com.ccapp.country.config.domain.enums;

import java.util.List;

public enum CountryAccountGroup {
    MEMBERSHIP_FEES("200", "Membership Fees", CountryChartAccountType.REVENUE),
    COMMISSION_POOL("210", "Commission Pool", CountryChartAccountType.LIABILITY),
    PLATFORM_REVENUE("220", "Platform Revenue", CountryChartAccountType.REVENUE),
    GUARANTEE_FUND("230", "Guarantee Fund", CountryChartAccountType.LIABILITY),
    PARTNER_SETTLEMENT("240", "Partner Settlement", CountryChartAccountType.LIABILITY),
    USER_WALLET("250", "User Wallet", CountryChartAccountType.LIABILITY);

    private final String prefix;
    private final String defaultLabel;
    private final CountryChartAccountType defaultAccountType;

    CountryAccountGroup(String prefix, String defaultLabel, CountryChartAccountType defaultAccountType) {
        this.prefix = prefix;
        this.defaultLabel = defaultLabel;
        this.defaultAccountType = defaultAccountType;
    }

    public String prefix() {
        return prefix;
    }

    public String defaultLabel() {
        return defaultLabel;
    }

    public CountryChartAccountType defaultAccountType() {
        return defaultAccountType;
    }

    public static List<CountryAccountGroup> defaultInitializationGroups() {
        return List.of(
                MEMBERSHIP_FEES,
                COMMISSION_POOL,
                PLATFORM_REVENUE,
                GUARANTEE_FUND
        );
    }

    public static List<CountryAccountGroup> requiredGroups() {
        return List.of(values());
    }
}
