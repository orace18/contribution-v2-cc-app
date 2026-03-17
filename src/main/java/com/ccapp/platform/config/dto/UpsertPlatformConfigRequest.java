package com.ccapp.platform.config.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record UpsertPlatformConfigRequest(
        @NotNull(message = "payload is required")
        JsonNode payload,
        OffsetDateTime effectiveDate
) {
}
