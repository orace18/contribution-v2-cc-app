package com.ccapp.wallet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record WalletWithdrawRequest(
        @NotNull
        @DecimalMin(value = "0.01", message = "Amount must be positive")
        BigDecimal amount,

        @NotBlank @Size(min = 3, max = 3) String currency,

        @NotBlank @Size(max = 128) String idempotencyKey
) {
}
