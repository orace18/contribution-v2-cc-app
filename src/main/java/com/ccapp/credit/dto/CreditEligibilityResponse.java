package com.ccapp.credit.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreditEligibilityResponse(
        UUID accountHolderId,
        UUID circleId,
        BigDecimal rmsScore,
        BigDecimal circleScore,
        BigDecimal eligibilityScore,
        BigDecimal maxEligibleAmount,
        OffsetDateTime evaluatedAt
) {
}
