package com.ccapp.seedcapital.readiness.dto;

import com.ccapp.seedcapital.readiness.domain.enums.CircleCoachAssignmentSource;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CircleCoachAssignmentResponse(
        UUID circleId,
        boolean assigned,
        UUID coachAccountHolderId,
        CircleCoachAssignmentSource source,
        String note,
        UUID assignedByAccountHolderId,
        OffsetDateTime assignedAt,
        OffsetDateTime updatedAt
) {
}
