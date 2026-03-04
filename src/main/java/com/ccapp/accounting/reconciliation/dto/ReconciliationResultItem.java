package com.ccapp.accounting.reconciliation.dto;

import java.math.BigDecimal;

public record ReconciliationResultItem(
        String pspReference,
        boolean matched,
        boolean adjustmentPosted,
        BigDecimal delta,
        String notes
) {
}
