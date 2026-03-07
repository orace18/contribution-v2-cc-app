package com.ccapp.admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TreasuryCashoutRequest(
        @NotBlank String accountCode,
        @NotNull @DecimalMin("0.0001") BigDecimal amount,
        @NotBlank String currency,
        String reason
) {
}
