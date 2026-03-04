package com.ccapp.subscription.dto;

import jakarta.validation.constraints.NotBlank;

public record InitiateSubscriptionRequest(
        @NotBlank(message = "planCode is required")
        String planCode,

        @NotBlank(message = "country is required")
        String country,

        String idempotencyKey
) {
}
