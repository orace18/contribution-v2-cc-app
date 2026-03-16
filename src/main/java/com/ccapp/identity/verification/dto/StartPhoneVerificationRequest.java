package com.ccapp.identity.verification.dto;

import com.ccapp.identity.verification.domain.enums.PhoneVerificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StartPhoneVerificationRequest(
        @NotBlank(message = "phoneNumber is required")
        @Size(max = 32, message = "phoneNumber max size is 32")
        String phoneNumber,

        @NotNull(message = "channel is required")
        PhoneVerificationChannel channel
) {
}
