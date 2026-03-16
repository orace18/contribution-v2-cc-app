package com.ccapp.circles.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CircleMemberFinancialsResponse(
        BigDecimal loanReceived,
        BigDecimal remainingBalance,
        long installmentsPaid,
        LocalDate nextInstallmentDate,
        String status
) {
}
