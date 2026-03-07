package com.ccapp.admin.dto;

import java.math.BigDecimal;

public record AdminDashboardResponse(
        long totalUsers,
        long activeCircles,
        long activeLoans,
        BigDecimal capitalAvailable,
        SubscriptionStatsResponse subscriptionStatistics
) {
}
