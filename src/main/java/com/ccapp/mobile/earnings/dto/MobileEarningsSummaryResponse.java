package com.ccapp.mobile.earnings.dto;

import java.math.BigDecimal;

public record MobileEarningsSummaryResponse(
        BigDecimal totalRms,
        BigDecimal weeklyGrowth,
        BigDecimal potential
) {
}
