package com.ccapp.platform.config.dto;

import com.ccapp.platform.config.domain.enums.PlatformConfigType;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

public record PlatformConfigSnapshotItemResponse(
        PlatformConfigType configType,
        Integer version,
        OffsetDateTime effectiveDate,
        JsonNode payload
) {
}
