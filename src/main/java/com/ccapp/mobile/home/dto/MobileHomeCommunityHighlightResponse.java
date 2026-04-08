package com.ccapp.mobile.home.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @deprecated Legacy home-screen community highlight card. Replace with Business Prayer feed/dashboard adapters.
 */
@Deprecated(since = "2026-04", forRemoval = false)
public record MobileHomeCommunityHighlightResponse(
        UUID id,
        String title,
        String content,
        String mediaUrl,
        OffsetDateTime featuredAt,
        OffsetDateTime createdAt
) {
}
