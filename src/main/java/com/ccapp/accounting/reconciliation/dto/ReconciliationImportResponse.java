package com.ccapp.accounting.reconciliation.dto;

import java.util.List;
import java.util.UUID;

public record ReconciliationImportResponse(
        UUID batchId,
        int total,
        int matched,
        int mismatched,
        int adjustmentsPosted,
        List<ReconciliationResultItem> items
) {
}
