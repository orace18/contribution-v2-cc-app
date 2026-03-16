package com.ccapp.circles.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CircleMemberHistoryItemResponse(
        String category,
        String eventType,
        String status,
        String description,
        BigDecimal amount,
        OffsetDateTime occurredAt,
        UUID referenceId
) {
}
