package com.ccapp.agent.dto;

import com.ccapp.agent.domain.enums.AgentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateAgentRequest(
        @NotNull(message = "appUserId is required")
        UUID appUserId,

        @NotNull(message = "agentType is required")
        AgentType agentType,

        UUID circleId,

        String status,

        @Size(max = 10, message = "permissions max size is 10")
        List<@Size(min = 2, max = 32, message = "permission size must be between 2 and 32") String> permissions
) {
}
