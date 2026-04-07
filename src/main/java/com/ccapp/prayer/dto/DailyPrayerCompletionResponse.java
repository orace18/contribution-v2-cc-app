package com.ccapp.prayer.dto;

import com.ccapp.prayer.domain.enums.PrayerDailyMissionCode;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DailyPrayerCompletionResponse(
        UUID dailyPrayerId,
        LocalDate prayerDate,
        OffsetDateTime completedAt,
        int currentStreak,
        int longestStreak,
        List<PrayerDailyMissionCode> completedMissionCodes
) {
}
