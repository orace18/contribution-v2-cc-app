package com.ccapp.prayer.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DailyPrayerResponse(
        UUID id,
        LocalDate prayerDate,
        String title,
        String scriptureReference,
        String content,
        List<PrayerMediaRefResponse> mediaRefs,
        boolean completedToday,
        OffsetDateTime publishedAt
) {
}
