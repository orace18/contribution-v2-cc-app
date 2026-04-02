package com.ccapp.mobile.payments.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MobilePaymentStatusResponse(
        UUID operationId,
        String status,
        String pspReference,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        OffsetDateTime lastUpdatedAt,
        String failureReason
) {
}
