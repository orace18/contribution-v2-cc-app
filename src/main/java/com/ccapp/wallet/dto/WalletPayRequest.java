package com.ccapp.wallet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record WalletPayRequest(
        /** Phone number that identifies the destination wallet (1 phone = 1 wallet). */
        @NotBlank String destinationPhone,

        @NotNull
        @DecimalMin(value = "0.01", message = "Amount must be positive")
        BigDecimal amount,

        @NotBlank @Size(min = 3, max = 3) String currency,

        @Size(max = 255) String description,

        @NotBlank @Size(max = 128) String idempotencyKey
) {
}
