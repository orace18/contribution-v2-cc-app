package com.ccapp.admin.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TreasuryOperationResponse(
        UUID operationId,
        String operationType,
        String message,
        OffsetDateTime processedAt
) {
}
