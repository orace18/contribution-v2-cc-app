package com.ccapp.admin.dto;

import com.ccapp.credit.domain.enums.LoanStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminLoanSummaryResponse(
        UUID loanId,
        UUID accountHolderId,
        UUID circleId,
        BigDecimal amount,
        String currency,
        BigDecimal interestRate,
        Integer durationMonths,
        LoanStatus status,
        OffsetDateTime approvedAt,
        OffsetDateTime disbursedAt,
        OffsetDateTime createdAt
) {
}
