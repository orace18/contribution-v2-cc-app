package com.ccapp.admin.dto;

import java.time.OffsetDateTime;

public record AdminMlmLedgerRebuildResponse(
        int batchSize,
        int processedOrders,
        int ordersWithCommissionPool,
        int ledgerEntriesRebuilt,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt
) {
}
