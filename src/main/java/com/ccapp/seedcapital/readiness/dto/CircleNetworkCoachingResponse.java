package com.ccapp.seedcapital.readiness.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CircleNetworkCoachingResponse(
        UUID circleId,
        boolean coachResolved,
        UUID coachAccountHolderId,
        int membersCount,
        int lineageCoverageCount,
        int lineageCoverageRequired,
        BigDecimal lineageCoverageScore,
        Integer coachLevel,
        List<UUID> advisorAccountHolderIds,
        String coachGrade,
        long coachTeamSize,
        BigDecimal networkSignalScore,
        OffsetDateTime coachLinkedAt
) {
}
