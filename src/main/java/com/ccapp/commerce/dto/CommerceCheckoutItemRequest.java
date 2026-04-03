package com.ccapp.commerce.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CommerceCheckoutItemRequest(
        @NotNull(message = "productId is required")
        UUID productId,

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be >= 1")
        Integer quantity
) {
}
