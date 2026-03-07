package com.ccapp.agent.dto;

import com.ccapp.agent.domain.enums.TransactionType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OfflineTransactionResponse(
        UUID id,
        UUID agentUserId,
        UUID accountHolderId,
        TransactionType transactionType,
        BigDecimal amount,
        String currency,
        String status,
        OffsetDateTime syncedAt,
        OffsetDateTime createdAt
) {
}
