package com.ccapp.credit.dto;

import com.ccapp.credit.domain.enums.InstallmentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RepaymentScheduleResponse(
        UUID id,
        Integer installmentNumber,
        BigDecimal amountDue,
        LocalDate dueDate,
        InstallmentStatus status
) {
}
