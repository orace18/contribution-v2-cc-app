package com.ccapp.progress.dto;

import com.ccapp.progress.domain.enums.TaskTriggerEvent;
import com.ccapp.progress.domain.enums.TaskType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TaskDefinitionRequest(
        @NotNull(message = "taskType is required")
        TaskType taskType,

        @NotBlank(message = "titleTemplate is required")
        @Size(max = 255, message = "titleTemplate max size is 255")
        String titleTemplate,

        @Size(max = 2000, message = "descriptionTemplate max size is 2000")
        String descriptionTemplate,

        @NotNull(message = "triggerEvent is required")
        TaskTriggerEvent triggerEvent,

        @NotNull(message = "actionJson is required")
        JsonNode actionJson,

        @Min(value = 0, message = "rewardPoints must be positive or zero")
        @Max(value = 100000, message = "rewardPoints is too large")
        int rewardPoints,

        boolean isActive,

        JsonNode metadataJson
) {
}
