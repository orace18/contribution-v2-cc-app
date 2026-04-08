package com.ccapp.prayer.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PrayerCommentResponse(
        UUID id,
        UUID postId,
        PrayerAuthorResponse author,
        String content,
        OffsetDateTime createdAt
) {
}
