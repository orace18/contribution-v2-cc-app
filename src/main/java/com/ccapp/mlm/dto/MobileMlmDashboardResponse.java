package com.ccapp.mlm.dto;

import java.math.BigDecimal;
import java.util.List;

public record MobileMlmDashboardResponse(
        long teamSize,
        long activeMembers,
        BigDecimal teamVolume,
        BigDecimal commissionsEarned,
        BigDecimal weeklyBonus,
        List<MobileMlmLevelBreakdownResponse> levelBreakdown
) {
}
