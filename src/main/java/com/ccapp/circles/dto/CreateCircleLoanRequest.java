package com.ccapp.circles.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateCircleLoanRequest(
        @NotNull(message = "borrowerAccountHolderId is required")
        UUID borrowerAccountHolderId,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be greater than 0")
        BigDecimal amount,

        @NotNull(message = "interest is required")
        @DecimalMin(value = "0.00", message = "interest must be greater than or equal to 0")
        BigDecimal interest,

        UUID advisorAccountHolderId
) {
}
