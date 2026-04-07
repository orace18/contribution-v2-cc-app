package com.ccapp.shop.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateShopProductRequest(
        @NotBlank(message = "name is required")
        @Size(max = 255, message = "name max size is 255")
        String name,

        @Size(max = 2000, message = "description max size is 2000")
        String description,

        @NotNull(message = "price is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "price must be greater than zero")
        @Digits(integer = 15, fraction = 4, message = "price precision is invalid")
        BigDecimal price,

        @NotBlank(message = "currency is required")
        @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO code")
        String currency,

        @NotNull(message = "commissionPercent is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "commissionPercent must be >= 0")
        @DecimalMax(value = "100.0", inclusive = true, message = "commissionPercent must be <= 100")
        @Digits(integer = 3, fraction = 2, message = "commissionPercent precision is invalid")
        BigDecimal commissionPercent,

        @NotNull(message = "cvValue is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "cvValue must be >= 0")
        @Digits(integer = 15, fraction = 4, message = "cvValue precision is invalid")
        BigDecimal cvValue,

        Boolean active,

        @Size(max = 128, message = "category max size is 128")
        String category,

        @Size(max = 512, message = "imageUrl max size is 512")
        String imageUrl
) {
}
