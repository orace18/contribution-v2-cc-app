package com.ccapp.payments.subscription.dto;

import jakarta.validation.constraints.NotBlank;

public record InitiateSubscriptionRequest(
        @NotBlank(message = "planCode is required")
        String planCode,

        String idempotencyKey
) {
}
