package com.ccapp.payments.psp.dto;

import java.util.UUID;

/** Maps to JawuPay {@code ConfirmPaymentResponse}. */
public record JawuPayConfirmResponse(
        UUID paymentId,
        String status,
        String pspProvider,
        String redirectUrl,
        String deepLinkUrl,
        String providerReference
) {
}
