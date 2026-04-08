package com.ccapp.prayer.dto;

import java.time.LocalDate;
import java.util.List;

public record PrayerStreakResponse(
        int currentStreak,
        int longestStreak,
        LocalDate lastCompletedDate,
        List<PrayerRecentDayResponse> recentDays
) {
}
