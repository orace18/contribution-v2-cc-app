package com.ccapp.admin.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AdminBackofficeProductRequest(
        @NotBlank String merchantId,
        @Size(min = 2, max = 3) String country,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 2000) String description,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal price,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) @Digits(integer = 15, fraction = 4) BigDecimal cvValue,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) @DecimalMax(value = "100.0", inclusive = true) @Digits(integer = 3, fraction = 2) BigDecimal commissionPercent,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) @Digits(integer = 15, fraction = 4) BigDecimal dsbAmount,
        @NotBlank String deliveryType,
        Boolean active,
        @Size(max = 512) String imageUrl
) {
}
