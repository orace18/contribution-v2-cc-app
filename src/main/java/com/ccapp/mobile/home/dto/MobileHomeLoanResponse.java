package com.ccapp.mobile.home.dto;

import com.ccapp.credit.domain.enums.LoanStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MobileHomeLoanResponse(
        UUID id,
        BigDecimal amount,
        String currency,
        LoanStatus status,
        OffsetDateTime createdAt
) {
}
