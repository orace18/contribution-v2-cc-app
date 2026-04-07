package com.ccapp.prayer.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PrayerCreateMediaUploadResponse(
        UUID mediaFileId,
        String uploadUrl,
        OffsetDateTime expiresAt,
        String bucket,
        String objectKey
) {
}
