package com.ccapp.wallet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record WalletReserveRequest(
        @NotNull
        @DecimalMin(value = "0.01", message = "Amount must be positive")
        BigDecimal amount,

        @NotBlank @Size(min = 3, max = 3) String currency,

        @NotBlank @Size(max = 255) String reason,

        /** Optional expiry date – reservation is auto-expired after this date. */
        OffsetDateTime expiryDate,

        @Pattern(regexp = "\\d{4,8}", message = "PIN must be 4 to 8 digits")
        String pin
) {
}
