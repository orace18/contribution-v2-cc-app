package com.ccapp.circles.dto;

import com.ccapp.circles.domain.enums.CircleLoanStatus;
import com.ccapp.circles.domain.enums.CircleRepaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CircleRepaymentResponse(
        UUID id,
        UUID loanId,
        BigDecimal amount,
        LocalDate dueDate,
        OffsetDateTime paidAt,
        CircleRepaymentStatus status,
        CircleLoanStatus loanStatus
) {
}
