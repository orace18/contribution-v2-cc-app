package com.ccapp.admin.dto;

import com.ccapp.circles.domain.enums.CircleStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminCircleSummaryResponse(
        UUID circleId,
        String name,
        UUID coachId,
        CircleStatus status,
        long membersCount,
        OffsetDateTime createdAt
) {
}
