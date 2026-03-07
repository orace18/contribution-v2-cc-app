package com.ccapp.media.storage;

import java.time.OffsetDateTime;

public record PresignedUrl(
        String url,
        OffsetDateTime expiresAt
) {
}
