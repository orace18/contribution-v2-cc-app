package com.ccapp.payments.commission.service;

import com.ccapp.payments.commission.domain.enums.CommissionPayoutBatchStatus;

import java.util.UUID;

public record CommissionPayoutProcessingResult(
        UUID batchId,
        int weekId,
        CommissionPayoutBatchStatus status,
        int paidItems,
        int failedItems
) {
}
