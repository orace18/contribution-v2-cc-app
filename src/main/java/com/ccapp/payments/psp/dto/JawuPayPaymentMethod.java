package com.ccapp.payments.psp.dto;

/** Maps to JawuPay {@code PaymentMethodOptionResponse} from {@code GET /api/v1/payment-methods}. */
public record JawuPayPaymentMethod(
        String id,
        String label
) {
}
