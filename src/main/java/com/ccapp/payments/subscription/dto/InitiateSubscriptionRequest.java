package com.ccapp.payments.subscription.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record InitiateSubscriptionRequest(
        @NotNull(message = "planId is required")
        UUID planId,

        String idempotencyKey,

        @Pattern(regexp = "\\d{4,8}", message = "PIN must be 4 to 8 digits")
        String pin,

        UUID otpChallengeId,

        @Pattern(regexp = "\\d{4,8}", message = "OTP code must be 4 to 8 digits")
        String otpCode
) {
}
