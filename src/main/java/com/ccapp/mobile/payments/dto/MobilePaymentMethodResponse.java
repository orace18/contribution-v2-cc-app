package com.ccapp.mobile.payments.dto;

public record MobilePaymentMethodResponse(
        String id,
        String name,
        String operator
) {
}
