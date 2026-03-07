package com.ccapp.credit.dto;

import com.ccapp.credit.domain.enums.LoanStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record LoanResponse(
        UUID id,
        UUID accountHolderId,
        UUID circleId,
        BigDecimal amount,
        String currency,
        BigDecimal interestRate,
        Integer durationMonths,
        LoanStatus status,
        OffsetDateTime approvedAt,
        OffsetDateTime disbursedAt,
        OffsetDateTime createdAt,
        List<RepaymentScheduleResponse> schedules
) {
}
