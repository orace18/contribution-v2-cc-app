package com.ccapp.circlesocial.dto;

import com.ccapp.circlesocial.domain.enums.ActivityType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CircleActivityResponse(
        UUID id,
        UUID circleId,
        UUID actorUserId,
        ActivityType activityType,
        UUID referenceId,
        String message,
        OffsetDateTime createdAt
) {
}
