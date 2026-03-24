package com.ccapp.payments.psp.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record JawupayPayoutRequest(
        UUID operationId,
        String idempotencyKey,
        BigDecimal amount,
        String currency,
        UUID accountHolderId,
        UUID actorUserId,
        String destinationPhone,
        String description
) {
}
