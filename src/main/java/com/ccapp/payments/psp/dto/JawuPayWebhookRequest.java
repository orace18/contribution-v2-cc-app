package com.ccapp.payments.psp.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record JawuPayWebhookRequest(
        @NotBlank(message = "provider is required")
        String provider,

        @NotBlank(message = "status is required")
        String status,

        String paymentId,
        String payoutId,
        String reference,
        String providerReference,
        UUID operationId,
        Long amount,
        String currency,
        String eventId
) {

    public JawuPayWebhookRequest(String provider,
                                 String status,
                                 String paymentId,
                                 String payoutId,
                                 String reference) {
        this(provider, status, paymentId, payoutId, reference, null, null, null, null, null);
    }

    public boolean hasPrimaryReference() {
        return hasText(paymentId) || hasText(payoutId) || hasText(resolvedProviderReference());
    }

    public String resolvedProviderReference() {
        if (hasText(providerReference)) {
            return providerReference.trim();
        }
        if (hasText(reference)) {
            return reference.trim();
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
