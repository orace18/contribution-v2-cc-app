package com.ccapp.payments.psp.dto;

import java.util.UUID;

/** Maps to JawuPay {@code POST /api/v1/payment/confirm} with {@code Idempotency-Key} header. */
public record JawuPayConfirmRequest(
        UUID quoteId,
        String currency,
        PayerInfo payerInfo
) {
    /** Mobile-money or card payer details. */
    public record PayerInfo(
            String msisdn,
            String operatorHint,
            String cardNumber
    ) {
        public static PayerInfo forMobileMoney(String msisdn, String operatorHint) {
            return new PayerInfo(msisdn, operatorHint, null);
        }
    }
}
