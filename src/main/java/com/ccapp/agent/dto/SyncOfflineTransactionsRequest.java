package com.ccapp.agent.dto;

import java.util.List;
import java.util.UUID;

public record SyncOfflineTransactionsRequest(
        List<UUID> transactionIds
) {
}
