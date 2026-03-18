package com.ccapp.admin.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminCountryWorkspaceTabResponse(
        String key,
        String label,
        AdminCountryWorkspaceStatus status,
        int readinessPercentage,
        List<String> missingItems,
        Integer version,
        OffsetDateTime effectiveDate,
        JsonNode payload
) {
}
