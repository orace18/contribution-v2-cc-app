package com.ccapp.chat.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateOrResolveConversationRequest(
        @NotEmpty List<@NotNull UUID> participants
) {
}
