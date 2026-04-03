package com.ccapp.commerce.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CommerceCheckoutRequest(
        @NotEmpty(message = "items are required")
        @Valid
        List<CommerceCheckoutItemRequest> items,

        @NotBlank(message = "paymentMethod is required")
        @Size(max = 32, message = "paymentMethod max size is 32")
        String paymentMethod,

        @NotNull(message = "deliveryAddressId is required")
        UUID deliveryAddressId,

        @Size(max = 64, message = "promoCode max size is 64")
        String promoCode
) {
}
