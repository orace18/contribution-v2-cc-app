package com.ccapp.ledger.reconciliation.dto;

import java.math.BigDecimal;

public record SettlementSummaryItem(
        String accountCode,
        String currency,
        BigDecimal balance
) {
}
