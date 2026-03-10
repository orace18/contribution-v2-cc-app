package com.ccapp.community.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CommunityPostResponse(
        UUID id,
        String title,
        String content,
        String mediaUrl,
        boolean featured,
        Integer featuredPriority,
        OffsetDateTime featuredAt,
        OffsetDateTime createdAt
) {
}
