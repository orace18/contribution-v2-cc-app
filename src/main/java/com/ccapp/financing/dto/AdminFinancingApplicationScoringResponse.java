package com.ccapp.financing.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminFinancingApplicationScoringResponse(
        UUID circleId,
        BigDecimal rmsScore,
        BigDecimal circleScore,
        BigDecimal eligibilityScore,
        BigDecimal maxEligibleAmount,
        OffsetDateTime evaluatedAt
) {
}
