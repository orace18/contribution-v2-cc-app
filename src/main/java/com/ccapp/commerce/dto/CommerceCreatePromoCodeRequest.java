package com.ccapp.commerce.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CommerceCreatePromoCodeRequest(
        @NotBlank(message = "code is required")
        @Size(max = 64, message = "code max size is 64")
        String code,

        @NotNull(message = "discountAmount is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "discountAmount must be > 0")
        @Digits(integer = 15, fraction = 4, message = "discountAmount precision is invalid")
        BigDecimal discountAmount,

        @NotBlank(message = "country is required")
        @Size(min = 2, max = 3, message = "country must be 2 to 3 characters")
        String country,

        @NotNull(message = "expiryDate is required")
        @Future(message = "expiryDate must be in the future")
        OffsetDateTime expiryDate,

        @NotNull(message = "usageLimit is required")
        @Min(value = 1, message = "usageLimit must be >= 1")
        Integer usageLimit
) {
}
