package com.ccapp.prayer.dto;

import com.ccapp.prayer.domain.enums.PrayerMediaUsageType;

import java.util.UUID;

public record PrayerMediaRefResponse(
        UUID mediaFileId,
        PrayerMediaUsageType usageType,
        int sortOrder,
        String contentType,
        String downloadUrl
) {
}
