package com.ccapp.circles.dto;

import java.util.UUID;

public record CircleReadinessChecklistResponse(
        UUID circleId,
        boolean ready,
        boolean allMembersInNetwork,
        boolean allMembershipsActive,
        boolean allWalletsActive,
        boolean allKycCompleted,
        boolean allImfLinked,
        boolean contributionsStarted,
        boolean reliabilityScoreReached,
        int completedItems,
        int totalItems) {
}
