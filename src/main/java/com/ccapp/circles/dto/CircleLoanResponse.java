package com.ccapp.circles.dto;

import com.ccapp.circles.domain.enums.CircleLoanStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CircleLoanResponse(
        UUID id,
        UUID cycleId,
        UUID borrowerAccountHolderId,
        BigDecimal amount,
        BigDecimal interest,
        CircleLoanStatus status,
        OffsetDateTime approvedAt,
        OffsetDateTime disbursedAt
) {
}
