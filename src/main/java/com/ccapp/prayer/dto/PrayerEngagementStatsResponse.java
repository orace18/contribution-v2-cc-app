package com.ccapp.prayer.dto;

public record PrayerEngagementStatsResponse(
        int requestsPublishedCount,
        int amenGivenCount,
        int supportGivenCount,
        int supportReceivedCount,
        int commentsWrittenCount,
        int commentsReceivedCount,
        int dailyPrayersCompletedCount
) {
}
