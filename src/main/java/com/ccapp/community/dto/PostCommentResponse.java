package com.ccapp.community.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @deprecated Replaced by {@code com.ccapp.prayer.dto.PrayerCommentResponse}.
 */
@Deprecated(since = "2026-04", forRemoval = false)
public record PostCommentResponse(
        UUID id,
        UUID postId,
        UUID accountHolderId,
        String content,
        UUID parentCommentId,
        List<PostCommentResponse> replies,
        OffsetDateTime createdAt
) {
}
