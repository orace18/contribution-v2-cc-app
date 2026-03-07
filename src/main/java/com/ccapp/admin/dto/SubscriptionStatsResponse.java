package com.ccapp.admin.dto;

public record SubscriptionStatsResponse(
        long total,
        long confirmed,
        long pending,
        long failed
) {
}
