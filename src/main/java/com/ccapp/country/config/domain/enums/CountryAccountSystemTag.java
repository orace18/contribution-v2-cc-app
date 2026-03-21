package com.ccapp.country.config.domain.enums;

import java.util.List;

public enum CountryAccountSystemTag {
    USER_WALLET,
    PLATFORM_REVENUE,
    MLM_POOL,
    CIRCLE_POOL,
    LOAN_PORTFOLIO,
    INTEREST_REVENUE,
    PSP_CLEARING;

    public static List<CountryAccountSystemTag> requiredTags() {
        return List.of(values());
    }
}
