package com.ccapp.seedcapital.readiness.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CircleTrustScoreResponse(
        UUID circleId,
        int membersCount,
        int financiallyReadyMembersCount,
        int completedMeetingsCount,
        int recentActivityCount,
        boolean coachAssigned,
        BigDecimal membershipComplianceScore,
        BigDecimal circleSizeScore,
        BigDecimal meetingsScore,
        BigDecimal activityScore,
        BigDecimal coachScore,
        BigDecimal globalScore,
        OffsetDateTime updatedAt
) {
}
