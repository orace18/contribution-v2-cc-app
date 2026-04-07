package com.ccapp.prayer.dto;

import com.ccapp.media.domain.enums.MediaStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PrayerMediaUploadConfirmationResponse(
        UUID mediaFileId,
        String contentType,
        Long size,
        MediaStatus status,
        OffsetDateTime createdAt
) {
}
