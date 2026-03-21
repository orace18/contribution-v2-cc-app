package com.ccapp.country.config.domain.enums;

import java.util.List;

public enum CountryAccountGroup {
    CAPITAL("10", "Capital", CountryChartAccountType.LIABILITY),
    FUND_STATUTAIRE("14", "Fund Statutaire", CountryChartAccountType.LIABILITY),
    USER_WALLET("46", "User Wallet", CountryChartAccountType.LIABILITY),
    PSP_CLEARING("47", "PSP Clearing", CountryChartAccountType.ASSET),
    BANKS("52", "Banks", CountryChartAccountType.ASSET),
    CASH("54", "Cash", CountryChartAccountType.ASSET),
    MLM_POOL("68", "MLM Pool", CountryChartAccountType.LIABILITY),
    SALES("70", "Sales", CountryChartAccountType.REVENUE),
    LOAN_PORTFOLIO("72", "Loan Portfolio", CountryChartAccountType.ASSET),
    FINTECH_REVENUE("75", "Fintech Revenue", CountryChartAccountType.REVENUE),
    INTEREST_REVENUE("76", "Interest Revenue", CountryChartAccountType.REVENUE),
    MEMBERSHIP_FEES("200", "Membership Fees", CountryChartAccountType.REVENUE),
    COMMISSION_POOL("210", "Commission Pool", CountryChartAccountType.LIABILITY),
    PLATFORM_REVENUE("220", "Platform Revenue", CountryChartAccountType.REVENUE),
    GUARANTEE_FUND("230", "Guarantee Fund", CountryChartAccountType.LIABILITY),
    PARTNER_SETTLEMENT("240", "Partner Settlement", CountryChartAccountType.LIABILITY);

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
                CAPITAL,
                FUND_STATUTAIRE,
                USER_WALLET,
                PSP_CLEARING,
                BANKS,
                CASH,
                MLM_POOL,
                SALES,
                LOAN_PORTFOLIO,
                FINTECH_REVENUE,
                INTEREST_REVENUE
        );
    }

    public static List<CountryAccountGroup> requiredGroups() {
        return defaultInitializationGroups();
    }
}
