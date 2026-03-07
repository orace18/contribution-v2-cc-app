package com.ccapp.capital.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CapitalPoolResponse(
        UUID id,
        UUID fundId,
        UUID circleId,
        BigDecimal allocatedAmount,
        BigDecimal usedAmount,
        BigDecimal remainingAmount
) {
}
