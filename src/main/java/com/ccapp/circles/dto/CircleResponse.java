package com.ccapp.circles.dto;

import com.ccapp.circles.domain.enums.CircleStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CircleResponse(
        UUID id,
        String name,
        @Deprecated(since = "2026-04", forRemoval = false)
        UUID coachId,
        CircleStatus status,
        OffsetDateTime createdAt,
        UUID conversationId,
        int membersCount
) {
}
