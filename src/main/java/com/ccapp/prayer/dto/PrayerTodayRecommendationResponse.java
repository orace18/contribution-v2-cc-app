package com.ccapp.prayer.dto;

import com.ccapp.prayer.domain.enums.PrayerDailyPrayerResponseType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response for GET /api/mobile/prayers/today.
 * Extends the daily prayer shape with recommendation metadata:
 * user streak and community support count.
 */
public record PrayerTodayRecommendationResponse(
        UUID id,
        LocalDate prayerDate,
        String title,
        String scriptureReference,
        String scriptureText,
        String shortContent,
        String content,
        String declaration,
        List<PrayerMediaRefResponse> mediaRefs,
        boolean completedToday,
        PrayerDailyPrayerResponseType todayResponseType,
        OffsetDateTime publishedAt,
        // Enriched recommendation fields
        int currentStreak,
        int longestStreak,
        int supportReceivedCount
) {
}
