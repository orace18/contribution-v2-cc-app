package com.ccapp.prayer.dto;

import com.ccapp.prayer.domain.enums.PrayerDailyMissionCode;
import com.ccapp.prayer.domain.enums.PrayerDailyMissionStatus;

import java.time.OffsetDateTime;

public record PrayerMissionProgressResponse(
        PrayerDailyMissionCode missionCode,
        String title,
        String description,
        PrayerDailyMissionStatus status,
        int progressValue,
        int targetValue,
        OffsetDateTime completedAt
) {
}
