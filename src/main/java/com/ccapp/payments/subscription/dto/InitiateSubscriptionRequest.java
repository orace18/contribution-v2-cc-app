package com.ccapp.payments.subscription.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InitiateSubscriptionRequest(
        @NotNull(message = "planId is required")
        UUID planId,

        String idempotencyKey
) {
}
