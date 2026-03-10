package com.ccapp.admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AdminSubscriptionPlanRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank @Size(min = 2, max = 3) String country,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotNull @DecimalMin("0.0001") BigDecimal price,
        boolean active
) {
}
