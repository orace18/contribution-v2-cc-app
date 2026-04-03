package com.ccapp.events.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateEventTicketTypeRequest(
        @NotBlank(message = "name is required")
        @Size(max = 64, message = "name max size is 64")
        String name,

        @NotNull(message = "price is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "price must be non-negative")
        @Digits(integer = 15, fraction = 4, message = "price must have up to 15 integer digits and 4 decimals")
        BigDecimal price,

        @NotBlank(message = "currency is required")
        @Size(min = 3, max = 3, message = "currency must be 3 characters")
        String currency,

        @NotNull(message = "stock is required")
        @PositiveOrZero(message = "stock must be non-negative")
        Integer stock,

        @NotNull(message = "cv is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "cv must be non-negative")
        @Digits(integer = 15, fraction = 4, message = "cv must have up to 15 integer digits and 4 decimals")
        BigDecimal cv,

        @NotNull(message = "platformCommissionRate is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "platformCommissionRate must be non-negative")
        @Digits(integer = 3, fraction = 2, message = "platformCommissionRate must have up to 3 integer digits and 2 decimals")
        BigDecimal platformCommissionRate,

        @NotNull(message = "dsbAmount is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "dsbAmount must be non-negative")
        @Digits(integer = 15, fraction = 4, message = "dsbAmount must have up to 15 integer digits and 4 decimals")
        BigDecimal dsbAmount
) {
}
