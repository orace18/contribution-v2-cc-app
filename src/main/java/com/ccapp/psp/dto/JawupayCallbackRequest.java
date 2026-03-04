package com.ccapp.psp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record JawupayCallbackRequest(
        @NotBlank(message = "pspReference is required")
        String pspReference,

        @NotNull(message = "amount is required")
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        String currency,

        @NotBlank(message = "status is required")
        String status,

        @NotBlank(message = "nonce is required")
        String nonce,

        @NotNull(message = "timestamp is required")
        Long timestamp
) {
}
