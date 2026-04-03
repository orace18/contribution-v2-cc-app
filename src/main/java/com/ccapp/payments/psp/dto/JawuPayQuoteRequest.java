package com.ccapp.payments.psp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Maps to JawuPay {@code POST /api/v1/payment/quote}.
 * <p>
 * {@code amount} is in minor units (e.g. 150000 for 1 500 XOF).
 * {@code clientId} is our internal reference for the payer (accountHolderId).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JawuPayQuoteRequest(
        long amount,
        String country,
        String paymentMethodId,
        String clientId,
        String operatorHint
) {
}
