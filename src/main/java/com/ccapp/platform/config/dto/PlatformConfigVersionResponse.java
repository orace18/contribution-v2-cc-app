package com.ccapp.platform.config.dto;

import com.ccapp.platform.config.domain.enums.PlatformConfigType;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PlatformConfigVersionResponse(
        UUID id,
        PlatformConfigType configType,
        Integer version,
        OffsetDateTime effectiveDate,
        JsonNode payload,
        UUID createdByAdminUserId,
        OffsetDateTime createdAt
) {
}
