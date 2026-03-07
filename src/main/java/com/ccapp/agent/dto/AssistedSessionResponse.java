package com.ccapp.agent.dto;

import com.ccapp.agent.domain.enums.SessionType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AssistedSessionResponse(
        UUID id,
        UUID agentUserId,
        UUID accountHolderId,
        SessionType sessionType,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt
) {
}
