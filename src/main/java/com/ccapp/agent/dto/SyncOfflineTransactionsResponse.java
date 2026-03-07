package com.ccapp.agent.dto;

import java.util.List;

public record SyncOfflineTransactionsResponse(
        int syncedCount,
        int failedCount,
        List<OfflineTransactionResponse> transactions
) {
}
