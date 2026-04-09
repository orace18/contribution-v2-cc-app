package com.ccapp.admin.dto;

import com.ccapp.circles.domain.enums.CircleStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminCircleSummaryResponse(
        UUID circleId,
        String name,
        @Deprecated(since = "2026-04", forRemoval = false)
        UUID coachId,
        CircleStatus status,
        long membersCount,
        OffsetDateTime createdAt
) {
}
