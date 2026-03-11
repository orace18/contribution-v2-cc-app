package com.ccapp.mlm.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MlmPlacementRequest(
        @NotNull(message = "accountHolderId is required")
        UUID accountHolderId,

        @NotNull(message = "sponsorAccountHolderId is required")
        UUID sponsorAccountHolderId
) {
}
