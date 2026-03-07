package com.ccapp.media.domain.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ConfirmUploadRequest(
        @NotNull(message = "mediaId is required")
        UUID mediaId
) {
}
