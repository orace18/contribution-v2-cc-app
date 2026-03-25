package com.ccapp.mobile.payments.dto;

import java.util.UUID;

public record MobilePaymentInitiateResponse(
        UUID operationId,
        String status,
        String pspReference,
        String checkoutUrl
) {
}
