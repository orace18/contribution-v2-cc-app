package com.ccapp.prayer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PrayerCreateMediaUploadRequest(
        @NotBlank(message = "contentType is required")
        @Size(max = 128, message = "contentType max size is 128")
        String contentType,

        @NotNull(message = "size is required")
        @Positive(message = "size must be > 0")
        Long size,

        @Size(max = 255, message = "originalFileName max size is 255")
        String originalFileName
) {
}
