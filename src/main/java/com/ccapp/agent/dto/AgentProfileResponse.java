package com.ccapp.agent.dto;

import com.ccapp.agent.domain.enums.AgentType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AgentProfileResponse(
        UUID id,
        UUID appUserId,
        AgentType agentType,
        UUID circleId,
        String status,
        OffsetDateTime createdAt,
        List<String> permissions
) {
}
