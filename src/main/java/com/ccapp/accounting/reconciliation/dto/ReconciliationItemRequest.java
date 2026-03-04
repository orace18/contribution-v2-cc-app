package com.ccapp.accounting.reconciliation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ReconciliationItemRequest(
        @NotBlank(message = "pspReference is required")
        String pspReference,

        @NotNull(message = "amount is required")
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        String currency
) {
}
