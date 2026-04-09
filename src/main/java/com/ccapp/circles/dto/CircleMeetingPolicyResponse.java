package com.ccapp.circles.dto;

import com.ccapp.circles.domain.enums.CircleMeetingFrequency;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CircleMeetingPolicyResponse(
        UUID circleId,
        CircleMeetingFrequency frequency,
        OffsetDateTime updatedAt
) {
}
