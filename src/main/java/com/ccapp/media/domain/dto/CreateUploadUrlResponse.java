package com.ccapp.media.domain.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateUploadUrlResponse(
        UUID mediaId,
        String bucket,
        String objectKey,
        String uploadUrl,
        OffsetDateTime expiresAt
) {
}
