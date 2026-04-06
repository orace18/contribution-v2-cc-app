package com.ccapp.mobile.payments.dto;

import java.util.UUID;

public record MobilePaymentInitiateResponse(
        UUID operationId,
        String status,
        String pspReference,
        String checkoutUrl,
        UUID existingOperationId,
        String existingStatus,
        boolean duplicateRequest,
        String message
) {
    public MobilePaymentInitiateResponse(UUID operationId,
                                         String status,
                                         String pspReference,
                                         String checkoutUrl) {
        this(operationId, status, pspReference, checkoutUrl, null, null, false, null);
    }
}
