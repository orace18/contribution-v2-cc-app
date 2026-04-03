package com.ccapp.commerce.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CommerceOrderSettlementBeneficiaryResponse(
        UUID accountHolderId,
        String label,
        BigDecimal amount,
        String currency
) {
}
