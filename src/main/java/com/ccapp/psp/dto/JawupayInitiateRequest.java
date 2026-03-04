package com.ccapp.psp.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record JawupayInitiateRequest(
        UUID operationId,
        String idempotencyKey,
        BigDecimal amount,
        String currency,
        UUID userId,
        String description
) {
}
