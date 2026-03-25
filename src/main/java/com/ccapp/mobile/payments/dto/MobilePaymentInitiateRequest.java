package com.ccapp.mobile.payments.dto;

public record MobilePaymentInitiateRequest(
        String idempotencyKey
) {
}
