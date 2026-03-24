package com.ccapp.circles.dto;

import com.ccapp.circles.domain.enums.CircleStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record MyCircleResponse(
        UUID id,
        String name,
        UUID coachId,
        CircleStatus status,
        OffsetDateTime createdAt,
        UUID conversationId,
        List<CircleMemberResponse> members
) {
}
