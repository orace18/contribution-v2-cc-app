package com.ccapp.mobile.earnings.dto;

import com.ccapp.payments.commission.domain.enums.CommissionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record MobileEarningHistoryItemResponse(
        BigDecimal amount,
        String type,
        CommissionStatus status,
        OffsetDateTime date
) {
}
