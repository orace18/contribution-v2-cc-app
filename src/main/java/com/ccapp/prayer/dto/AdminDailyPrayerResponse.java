package com.ccapp.prayer.dto;

import com.ccapp.prayer.domain.enums.PrayerDailyPrayerStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminDailyPrayerResponse(
        UUID id,
        LocalDate prayerDate,
        PrayerDailyPrayerStatus status,
        String code,
        String title,
        String scriptureReference,
        String scriptureText,
        String shortContent,
        String content,
        String declaration,
        String category,
        String theme,
        String tone,
        String audience,
        String tagsCsv,
        String language,
        String countryScope,
        boolean featured,
        int priority,
        int estimatedReadSeconds,
        String audioStatus,
        List<PrayerMediaRefResponse> mediaRefs,
        UUID createdByAdminUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime publishedAt
) {
}
