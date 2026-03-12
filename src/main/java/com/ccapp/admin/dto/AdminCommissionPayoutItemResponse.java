package com.ccapp.admin.dto;

import com.ccapp.payments.commission.domain.enums.CommissionPayoutItemStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminCommissionPayoutItemResponse(
        UUID id,
        UUID accountHolderId,
        BigDecimal grossAmount,
        BigDecimal taxAmount,
        BigDecimal netAmount,
        CommissionPayoutItemStatus status,
        UUID operationId,
        OffsetDateTime processedAt
) {
}
