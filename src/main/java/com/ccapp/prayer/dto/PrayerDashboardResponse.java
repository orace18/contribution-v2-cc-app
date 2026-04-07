package com.ccapp.prayer.dto;

import java.time.LocalDate;
import java.util.List;

public record PrayerDashboardResponse(
        String timezone,
        LocalDate localDate,
        boolean dailyPrayerCompletedToday,
        PrayerStreakResponse streak,
        PrayerEngagementStatsResponse engagementStats,
        List<PrayerMissionProgressResponse> missions
) {
}
