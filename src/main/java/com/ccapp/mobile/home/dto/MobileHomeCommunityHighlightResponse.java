package com.ccapp.mobile.home.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MobileHomeCommunityHighlightResponse(
        UUID id,
        String title,
        String content,
        String mediaUrl,
        OffsetDateTime featuredAt,
        OffsetDateTime createdAt
) {
}
