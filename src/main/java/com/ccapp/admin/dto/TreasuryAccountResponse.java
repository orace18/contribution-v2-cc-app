package com.ccapp.admin.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TreasuryAccountResponse(
        String accountCode,
        String currency,
        BigDecimal balance,
        OffsetDateTime updatedAt
) {
}
