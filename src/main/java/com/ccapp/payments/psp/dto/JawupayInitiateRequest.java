package com.ccapp.payments.psp.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record JawupayInitiateRequest(
        UUID operationId,
        String idempotencyKey,
        BigDecimal amount,
        String currency,
        UUID accountHolderId,
        UUID actorUserId,
        String description
) {
}
