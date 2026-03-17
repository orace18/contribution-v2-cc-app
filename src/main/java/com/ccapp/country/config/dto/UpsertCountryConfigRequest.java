package com.ccapp.country.config.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record UpsertCountryConfigRequest(
        @NotNull(message = "payload is required")
        JsonNode payload,
        OffsetDateTime effectiveDate
) {
}
