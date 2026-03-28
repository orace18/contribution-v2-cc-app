package com.ccapp.payments.psp.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Maps to JawuPay {@code PaymentDetailsResponse} from {@code GET /api/v1/payment/{paymentId}}. */
public record JawuPayPaymentResponse(
        UUID paymentId,
        String clientId,
        String status,
        long amount,
        String currency,
        String country,
        String paymentMethodId,
        String pspProvider,
        String providerReference,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
