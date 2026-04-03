package com.ccapp.commerce.dto;

import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record CommercePayOrderRequest(
        @Pattern(regexp = "\\d{4,8}", message = "PIN must be 4 to 8 digits")
        String pin,

        UUID otpChallengeId,

        @Pattern(regexp = "\\d{4,8}", message = "OTP code must be 4 to 8 digits")
        String otpCode
) {
}
