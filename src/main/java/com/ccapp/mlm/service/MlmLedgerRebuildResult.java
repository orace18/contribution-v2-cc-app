package com.ccapp.mlm.service;

import java.time.OffsetDateTime;

public record MlmLedgerRebuildResult(
        int batchSize,
        int processedOrders,
        int ordersWithCommissionPool,
        int ledgerEntriesRebuilt,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt
) {
}
