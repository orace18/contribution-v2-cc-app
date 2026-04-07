package com.ccapp.community.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @deprecated Replaced by prayer post responses under {@code com.ccapp.prayer.dto}.
 */
@Deprecated(since = "2026-04", forRemoval = false)
public record CommunityPostResponse(
        UUID id,
        String title,
        String content,
        String mediaUrl,
        boolean featured,
        Integer featuredPriority,
        OffsetDateTime featuredAt,
        long likesCount,
        long commentsCount,
        boolean likedByCurrentUser,
        OffsetDateTime createdAt
) {
}
