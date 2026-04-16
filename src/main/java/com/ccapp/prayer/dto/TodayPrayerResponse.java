package com.ccapp.prayer.dto;

import com.ccapp.prayer.domain.enums.PrayerDailyPrayerResponseType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TodayPrayerResponse(
        UUID id,
        LocalDate localDate,
        String timezone,
        String title,
        String scriptureReference,
        String content,
        List<PrayerMediaRefResponse> mediaRefs,
        PrayerMediaRefResponse audioRef,
        boolean completedToday,
        PrayerDailyPrayerResponseType todayResponseType,
        OffsetDateTime publishedAt,
        int currentStreak,
        int longestStreak,
        long peoplePrayingForYou,
        String recommendationReason
) {
}
