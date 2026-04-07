package com.ccapp.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * @deprecated Replaced by {@code com.ccapp.prayer.dto.CreatePrayerCommentRequest}.
 */
@Deprecated(since = "2026-04", forRemoval = false)
public record PostCommentRequest(
        @NotBlank(message = "content is required")
        @Size(max = 1000, message = "content max size is 1000")
        String content,

        UUID parentCommentId
) {
}
