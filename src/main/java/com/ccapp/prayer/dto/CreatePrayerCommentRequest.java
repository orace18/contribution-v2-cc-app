package com.ccapp.prayer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreatePrayerCommentRequest(
        UUID clientRequestId,

        @NotBlank(message = "content is required")
        @Size(max = 2000, message = "content max size is 2000")
        String content
) {
}
