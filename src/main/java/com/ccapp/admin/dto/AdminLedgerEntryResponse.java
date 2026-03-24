package com.ccapp.admin.dto;

import com.ccapp.ledger.domain.enums.LedgerEntryType;
import com.ccapp.payments.operation.domain.enums.OperationType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminLedgerEntryResponse(
        UUID ledgerEntryId,
        UUID accountingEventId,
        UUID operationId,
        OperationType operationType,
        String accountCode,
        LedgerEntryType entryType,
        BigDecimal amount,
        String currency,
        String description,
        OffsetDateTime postedAt,
        OffsetDateTime createdAt
) {
}
