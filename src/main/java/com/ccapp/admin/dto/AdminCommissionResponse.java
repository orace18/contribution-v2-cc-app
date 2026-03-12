package com.ccapp.admin.dto;

import com.ccapp.payments.commission.domain.enums.CommissionStatus;
import com.ccapp.payments.commission.domain.enums.CommissionType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminCommissionResponse(
        UUID id,
        UUID beneficiaryAccountHolderId,
        BigDecimal amount,
        String currency,
        CommissionType commissionType,
        CommissionStatus status,
        Integer weekId,
        String source,
        OffsetDateTime createdAt,
        OffsetDateTime paidAt
) {
}
