package com.ccapp.capital.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record AllocateCapitalPoolRequest(
        @NotNull(message = "fundId is required")
        UUID fundId,

        @NotNull(message = "circleId is required")
        UUID circleId,

        @NotNull(message = "allocatedAmount is required")
        @DecimalMin(value = "0.01", message = "allocatedAmount must be > 0")
        BigDecimal allocatedAmount
) {
}
