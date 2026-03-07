package com.ccapp.admin.dto;

import com.ccapp.circles.domain.enums.CircleStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminCircleDetailsResponse(
        UUID circleId,
        String name,
        UUID coachId,
        CircleStatus status,
        OffsetDateTime createdAt,
        List<AdminCircleMemberResponse> members
) {
}
