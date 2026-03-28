package com.ccapp.payments.psp.dto;

import java.util.UUID;

/** Maps to JawuPay {@code CreatePayoutResponse} from {@code POST /api/v1/payout}. */
public record JawuPayCreatePayoutResponse(
        UUID payoutId,
        String status,
        String pspProvider,
        String providerReference
) {
}
