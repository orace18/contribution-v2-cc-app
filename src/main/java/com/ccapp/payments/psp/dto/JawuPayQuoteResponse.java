package com.ccapp.payments.psp.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Maps to JawuPay {@code PaymentQuoteResponse}. Amounts are in minor units. */
public record JawuPayQuoteResponse(
        UUID quoteId,
        long amount,
        long fees,
        long totalAmount,
        OffsetDateTime expiresAt
) {
}
