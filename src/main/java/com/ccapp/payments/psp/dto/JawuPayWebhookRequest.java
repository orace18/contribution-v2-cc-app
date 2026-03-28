package com.ccapp.payments.psp.dto;

import jakarta.validation.constraints.NotBlank;

public record JawuPayWebhookRequest(
        @NotBlank(message = "provider is required")
        String provider,

        @NotBlank(message = "status is required")
        String status,

        String paymentId,

        String reference
) {
}
