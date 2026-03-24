package com.ccapp.credit.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record RepayLoanRequest(
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be > 0")
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        @Size(min = 3, max = 3, message = "currency must be ISO-3")
        String currency,

        @NotBlank(message = "paymentMethod is required")
        @Size(max = 32, message = "paymentMethod max size is 32")
        String paymentMethod,

        UUID institutionId,

        String transactionId,

        @Pattern(regexp = "\\d{4,8}", message = "PIN must be 4 to 8 digits")
        String pin,

        UUID otpChallengeId,

        @Pattern(regexp = "\\d{4,8}", message = "OTP code must be 4 to 8 digits")
        String otpCode
) {
}
