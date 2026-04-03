package com.ccapp.commerce.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CommerceValidatePromoCodeRequest(
        @NotBlank(message = "code is required")
        @Size(max = 64, message = "code max size is 64")
        String code,

        @NotBlank(message = "country is required")
        @Size(min = 2, max = 3, message = "country must be 2 to 3 characters")
        String country,

        @NotNull(message = "cartAmount is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "cartAmount must be > 0")
        @Digits(integer = 15, fraction = 4, message = "cartAmount precision is invalid")
        BigDecimal cartAmount
) {
}
