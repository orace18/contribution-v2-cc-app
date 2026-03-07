package com.ccapp.progress.dto;

import com.ccapp.progress.domain.enums.TaskStatus;
import com.ccapp.progress.domain.enums.TaskType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserTaskResponse(
        UUID id,
        TaskType taskType,
        TaskStatus status,
        OffsetDateTime validatedAt,
        OffsetDateTime createdAt
) {
}
