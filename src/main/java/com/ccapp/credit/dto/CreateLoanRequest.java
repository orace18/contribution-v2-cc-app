package com.ccapp.credit.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateLoanRequest(
        @NotNull(message = "circleId is required")
        UUID circleId,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be > 0")
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        @Size(min = 3, max = 3, message = "currency must be ISO-3")
        String currency,

        @NotNull(message = "interestRate is required")
        @DecimalMin(value = "0.00", message = "interestRate must be >= 0")
        BigDecimal interestRate,

        @NotNull(message = "durationMonths is required")
        @Min(value = 1, message = "durationMonths must be >= 1")
        @Max(value = 60, message = "durationMonths must be <= 60")
        Integer durationMonths,

        String idempotencyKey
) {
}
