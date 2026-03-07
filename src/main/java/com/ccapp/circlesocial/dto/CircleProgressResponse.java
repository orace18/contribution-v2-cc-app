package com.ccapp.circlesocial.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CircleProgressResponse(
        UUID circleId,
        int membersCount,
        int membersCompletedTraining,
        int membersSubmittedProject,
        int membersPaidContribution,
        BigDecimal progressPercentage,
        OffsetDateTime updatedAt
) {
}
