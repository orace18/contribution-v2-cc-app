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
        String title,
        String scriptureReference,
        String content,
        List<PrayerMediaRefResponse> mediaRefs,
        UUID createdByAdminUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime publishedAt
) {
}
