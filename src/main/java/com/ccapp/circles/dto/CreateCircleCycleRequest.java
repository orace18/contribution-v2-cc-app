package com.ccapp.circles.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateCircleCycleRequest(
        @NotNull(message = "startDate is required")
        LocalDate startDate,

        @NotNull(message = "endDate is required")
        LocalDate endDate,

        @NotNull(message = "contributionAmount is required")
        @DecimalMin(value = "0.01", message = "contributionAmount must be greater than 0")
        BigDecimal contributionAmount
) {
}
