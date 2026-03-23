package com.ccapp.country.config.domain.enums;

import java.util.Arrays;
import java.util.List;

public enum CountryMasterAccountTemplate {
    CAPITAL(10, "Capital", CountryChartAccountType.LIABILITY,
            "Core capital reserve for country activation",
            false, CountryAccountGroup.CAPITAL, null),
    FUND_STATUTAIRE(14, "Fund Statutaire", CountryChartAccountType.LIABILITY,
            "Statutory and circle reserve pool",
            true, CountryAccountGroup.FUND_STATUTAIRE, CountryAccountSystemTag.CIRCLE_POOL),
    MEMBERS_WALLET(46, "Members Wallet", CountryChartAccountType.LIABILITY,
            "Members wallet control account",
            true, CountryAccountGroup.USER_WALLET, CountryAccountSystemTag.USER_WALLET),
    PSP_CLEARING(47, "PSP Clearing", CountryChartAccountType.ASSET,
            "Payment service provider clearing account",
            true, CountryAccountGroup.PSP_CLEARING, CountryAccountSystemTag.PSP_CLEARING),
    BANKS(52, "Banks", CountryChartAccountType.ASSET,
            "Bank cash and treasury accounts",
            false, CountryAccountGroup.BANKS, null),
    CASH(54, "Cash", CountryChartAccountType.ASSET,
            "Cash-on-hand operational accounts",
            false, CountryAccountGroup.CASH, null),
    MARKETPLACE_POOL(71, "Marketplace Pool", CountryChartAccountType.LIABILITY,
            "Central marketplace clearing pool per country",
            true, CountryAccountGroup.MARKETPLACE_POOL, CountryAccountSystemTag.MARKETPLACE_POOL),
    MLM_POOL(68, "MLM Pool", CountryChartAccountType.LIABILITY,
            "MLM commissions and bonus pool",
            true, CountryAccountGroup.MLM_POOL, CountryAccountSystemTag.MLM_POOL),
    SALES(70, "Sales", CountryChartAccountType.REVENUE,
            "Contribution and sales revenue accounts",
            false, CountryAccountGroup.SALES, null),
    LOAN_PORTFOLIO(72, "Loan Portfolio", CountryChartAccountType.ASSET,
            "Loan principal portfolio account",
            true, CountryAccountGroup.LOAN_PORTFOLIO, CountryAccountSystemTag.LOAN_PORTFOLIO),
    FINTECH_REVENUE(75, "Fintech Revenue", CountryChartAccountType.REVENUE,
            "Platform fees and fintech revenue",
            true, CountryAccountGroup.FINTECH_REVENUE, CountryAccountSystemTag.PLATFORM_REVENUE),
    INTEREST_REVENUE(76, "Interest Revenue", CountryChartAccountType.REVENUE,
            "Interest income recognition account",
            true, CountryAccountGroup.INTEREST_REVENUE, CountryAccountSystemTag.INTEREST_REVENUE);

    private final int groupId;
    private final String name;
    private final CountryChartAccountType accountType;
    private final String description;
    private final boolean systemRequired;
    private final CountryAccountGroup accountGroup;
    private final CountryAccountSystemTag systemTag;

    CountryMasterAccountTemplate(int groupId,
                                 String name,
                                 CountryChartAccountType accountType,
                                 String description,
                                 boolean systemRequired,
                                 CountryAccountGroup accountGroup,
                                 CountryAccountSystemTag systemTag) {
        this.groupId = groupId;
        this.name = name;
        this.accountType = accountType;
        this.description = description;
        this.systemRequired = systemRequired;
        this.accountGroup = accountGroup;
        this.systemTag = systemTag;
    }

    public int groupId() {
        return groupId;
    }

    public String accountNumberPrefix() {
        return Integer.toString(groupId);
    }

    public String displayName() {
        return name;
    }

    public CountryChartAccountType accountType() {
        return accountType;
    }

    public String description() {
        return description;
    }

    public boolean systemRequired() {
        return systemRequired;
    }

    public CountryAccountGroup accountGroup() {
        return accountGroup;
    }

    public CountryAccountSystemTag systemTag() {
        return systemTag;
    }

    public static List<CountryMasterAccountTemplate> requiredTemplates() {
        return Arrays.stream(values())
                .filter(CountryMasterAccountTemplate::systemRequired)
                .toList();
    }

    public static CountryMasterAccountTemplate fromGroupId(int groupId) {
        return Arrays.stream(values())
                .filter(template -> template.groupId == groupId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported account group id: " + groupId));
    }

    public static CountryMasterAccountTemplate fromSystemTag(CountryAccountSystemTag systemTag) {
        return Arrays.stream(values())
                .filter(template -> template.systemTag == systemTag)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported system tag: " + systemTag));
    }
}
