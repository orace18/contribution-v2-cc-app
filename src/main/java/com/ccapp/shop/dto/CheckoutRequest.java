package com.ccapp.shop.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CheckoutRequest(
        @NotEmpty(message = "items are required")
        @Valid
        List<CheckoutItemRequest> items,

        @NotBlank(message = "paymentMethod is required")
        @Size(max = 32, message = "paymentMethod max size is 32")
        String paymentMethod,

        @NotNull(message = "deliveryAddressId is required")
        UUID deliveryAddressId
) {
}
