package com.ccapp.identity.managedaccounts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateManagedAccountRequest(
        @NotBlank(message = "firstName is required")
        @Size(max = 120, message = "firstName max size is 120")
        String firstName,

        @NotBlank(message = "lastName is required")
        @Size(max = 120, message = "lastName max size is 120")
        String lastName,

        @NotBlank(message = "phoneNumber is required")
        @Size(max = 32, message = "phoneNumber max size is 32")
        String phoneNumber,

        @NotBlank(message = "countryCode is required")
        @Size(max = 3, message = "countryCode max size is 3")
        String countryCode,

        @NotNull(message = "phoneVerificationId is required")
        UUID phoneVerificationId
) {
}
