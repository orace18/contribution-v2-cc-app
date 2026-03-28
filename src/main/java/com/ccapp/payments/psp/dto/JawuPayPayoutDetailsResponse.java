package com.ccapp.payments.psp.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Maps to JawuPay {@code PayoutDetailsResponse} from {@code GET /api/v1/payout/{id}}. */
public record JawuPayPayoutDetailsResponse(
        UUID payoutId,
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
