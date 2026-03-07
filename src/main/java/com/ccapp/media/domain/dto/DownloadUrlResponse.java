package com.ccapp.media.domain.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DownloadUrlResponse(
        UUID mediaId,
        String downloadUrl,
        OffsetDateTime expiresAt
) {
}
