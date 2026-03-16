package com.ccapp.identity.verification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConfirmPhoneVerificationRequest(
        @NotBlank(message = "code is required")
        @Pattern(regexp = "\\d{4,8}", message = "code must contain 4 to 8 digits")
        String code
) {
}
