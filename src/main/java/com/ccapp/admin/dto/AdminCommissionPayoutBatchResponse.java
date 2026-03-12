package com.ccapp.admin.dto;

import com.ccapp.payments.commission.domain.enums.CommissionPayoutBatchStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminCommissionPayoutBatchResponse(
        UUID id,
        int weekId,
        CommissionPayoutBatchStatus status,
        OffsetDateTime createdAt,
        List<AdminCommissionPayoutItemResponse> items
) {
}
