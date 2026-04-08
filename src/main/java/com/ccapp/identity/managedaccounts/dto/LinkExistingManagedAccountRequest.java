package com.ccapp.identity.managedaccounts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record LinkExistingManagedAccountRequest(
        @NotBlank(message = "phoneNumber is required")
        @Size(max = 32, message = "phoneNumber max size is 32")
        String phoneNumber,

        @NotNull(message = "phoneVerificationId is required")
        UUID phoneVerificationId
) {
}
