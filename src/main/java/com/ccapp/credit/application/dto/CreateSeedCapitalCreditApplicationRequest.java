package com.ccapp.credit.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateSeedCapitalCreditApplicationRequest(
        @NotNull(message = "circleId is required")
        UUID circleId,

        @NotNull(message = "requestedAmount is required")
        @DecimalMin(value = "0.01", message = "requestedAmount must be > 0")
        BigDecimal requestedAmount,

        @Min(value = 1, message = "requestedDurationMonths must be >= 1")
        @Max(value = 60, message = "requestedDurationMonths must be <= 60")
        Integer requestedDurationMonths,

        @DecimalMin(value = "0.00", message = "requestedInterestRate must be >= 0")
        BigDecimal requestedInterestRate
) {
}
