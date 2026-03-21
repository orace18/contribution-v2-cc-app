package com.ccapp.progress.dto;

import com.ccapp.progress.domain.enums.TaskTriggerEvent;
import com.ccapp.progress.domain.enums.TaskType;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TaskDefinitionResponse(
        UUID id,
        TaskType taskType,
        String titleTemplate,
        String descriptionTemplate,
        TaskTriggerEvent triggerEvent,
        JsonNode actionJson,
        int rewardPoints,
        boolean isActive,
        JsonNode metadataJson,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
