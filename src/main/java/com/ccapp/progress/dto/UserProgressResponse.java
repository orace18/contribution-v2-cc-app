package com.ccapp.progress.dto;

import com.ccapp.progress.domain.enums.TaskType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record UserProgressResponse(
        UUID userId,
        TaskType currentStep,
        BigDecimal progressPercentage,
        TaskType nextTask,
        OffsetDateTime updatedAt
) {
}
