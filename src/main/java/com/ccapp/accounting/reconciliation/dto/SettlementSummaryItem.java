package com.ccapp.accounting.reconciliation.dto;

import java.math.BigDecimal;

public record SettlementSummaryItem(
        String accountCode,
        String currency,
        BigDecimal balance
) {
}
