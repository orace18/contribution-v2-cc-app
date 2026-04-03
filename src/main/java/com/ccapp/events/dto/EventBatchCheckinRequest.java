package com.ccapp.events.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record EventBatchCheckinRequest(
        @NotEmpty(message = "items are required")
        @Valid
        List<EventBatchCheckinItemRequest> items
) {
}
