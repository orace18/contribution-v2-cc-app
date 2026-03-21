package com.ccapp.progress.dto;

import com.ccapp.progress.domain.enums.TaskStatus;
import com.ccapp.progress.domain.enums.TaskType;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        TaskType type,
        String title,
        String description,
        TaskStatus status,
        int priority,
        OffsetDateTime dueDate,
        JsonNode action
) {
}
