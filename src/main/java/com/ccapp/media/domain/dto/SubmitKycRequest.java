package com.ccapp.media.domain.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SubmitKycRequest(
        @NotNull(message = "accountHolderId is required")
        UUID accountHolderId
) {
}
