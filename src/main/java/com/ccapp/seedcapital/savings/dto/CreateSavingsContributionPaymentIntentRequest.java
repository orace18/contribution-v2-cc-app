package com.ccapp.seedcapital.savings.dto;

import com.ccapp.seedcapital.savings.domain.enums.SavingsContributionPaymentProvider;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateSavingsContributionPaymentIntentRequest(
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be positive")
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO code")
        String currency,

        @NotNull(message = "provider is required")
        SavingsContributionPaymentProvider provider,

        @NotBlank(message = "idempotencyKey is required")
        @Size(max = 128, message = "idempotencyKey must be <= 128 characters")
        String idempotencyKey,

        @Pattern(regexp = "\\d{4,8}", message = "PIN must be 4 to 8 digits")
        String pin,

        UUID otpChallengeId,

        @Pattern(regexp = "\\d{4,8}", message = "OTP code must be 4 to 8 digits")
        String otpCode
) {
}
