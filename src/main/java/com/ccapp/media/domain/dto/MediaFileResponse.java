package com.ccapp.media.domain.dto;

import com.ccapp.media.domain.enums.MediaStatus;
import com.ccapp.media.domain.enums.MediaType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MediaFileResponse(
        UUID id,
        UUID accountHolderId,
        MediaType type,
        String bucket,
        String objectKey,
        String publicUrl,
        String contentType,
        Long size,
        MediaStatus status,
        OffsetDateTime createdAt
) {
}
