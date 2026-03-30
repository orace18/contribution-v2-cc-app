package com.ccapp.payments.psp.dto;

import jakarta.validation.constraints.NotBlank;

public record JawuPayWebhookRequest(
        @NotBlank(message = "provider is required")
        String provider,

        @NotBlank(message = "status is required")
        String status,

        String paymentId,
        String payoutId,

        String reference
) {
    public boolean hasPrimaryReference() {
        return hasText(paymentId) || hasText(payoutId);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
