package com.ccapp.agent.dto;

import com.ccapp.agent.domain.enums.SessionType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateAssistedSessionRequest(
        @NotNull(message = "accountHolderId is required")
        UUID accountHolderId,

        @NotNull(message = "sessionType is required")
        SessionType sessionType
) {
}
