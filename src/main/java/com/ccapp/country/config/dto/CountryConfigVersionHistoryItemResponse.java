package com.ccapp.country.config.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CountryConfigVersionHistoryItemResponse(
        Integer version,
        OffsetDateTime effectiveDate,
        JsonNode payload,
        boolean active,
        UUID createdByAdminUserId,
        OffsetDateTime createdAt
) {
}
