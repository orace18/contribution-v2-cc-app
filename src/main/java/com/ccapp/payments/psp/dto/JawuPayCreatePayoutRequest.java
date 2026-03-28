package com.ccapp.payments.psp.dto;

/**
 * Maps to JawuPay {@code POST /api/v1/payout} ({@code CreatePayoutRequest}).
 * <p>
 * {@code amount} is in minor units. {@code clientId} is our accountHolderId as string.
 * {@code beneficiaryRef} is the recipient phone number or account reference.
 */
public record JawuPayCreatePayoutRequest(
        long amount,
        String currency,
        String country,
        String paymentMethodId,
        String clientId,
        String beneficiaryRef,
        String operatorHint
) {
}
