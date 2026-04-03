package com.ccapp.payments.psp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/** Maps to JawuPay {@code POST /api/v1/payment/confirm} with {@code Idempotency-Key} header. */
public record JawuPayConfirmRequest(
        UUID quoteId,
        String currency,
        PayerInfo payerInfo
) {
    /** Mobile-money or card payer details. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
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
