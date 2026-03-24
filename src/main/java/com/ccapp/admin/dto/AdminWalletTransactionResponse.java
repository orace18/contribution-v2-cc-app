package com.ccapp.admin.dto;

import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminWalletTransactionResponse(
        UUID operationId,
        OperationType type,
        OperationStatus status,
        String direction,
        BigDecimal amount,
        String currency,
        String description,
        String idempotencyKey,
        String destinationPhone,
        OffsetDateTime createdAt
) {
}
