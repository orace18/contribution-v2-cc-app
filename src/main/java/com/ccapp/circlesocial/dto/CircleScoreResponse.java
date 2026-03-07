package com.ccapp.circlesocial.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CircleScoreResponse(
        UUID circleId,
        BigDecimal activityScore,
        BigDecimal trainingScore,
        BigDecimal repaymentScore,
        BigDecimal globalScore,
        OffsetDateTime updatedAt
) {
}
