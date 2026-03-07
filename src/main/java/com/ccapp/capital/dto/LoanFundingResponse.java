package com.ccapp.capital.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LoanFundingResponse(
        UUID id,
        UUID loanId,
        UUID fundId,
        BigDecimal fundedAmount,
        OffsetDateTime fundedAt
) {
}
