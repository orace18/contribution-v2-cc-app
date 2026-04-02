package com.ccapp.identity.verification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FirebasePhoneVerificationRequest(
        @NotBlank(message = "firebaseToken is required")
        String firebaseToken,

        @NotBlank(message = "phoneNumber is required")
        @Size(max = 32, message = "phoneNumber max size is 32")
        String phoneNumber
) {
}
