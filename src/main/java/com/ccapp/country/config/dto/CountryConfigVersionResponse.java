package com.ccapp.country.config.dto;

import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CountryConfigVersionResponse(
        UUID id,
        String countryCode,
        CountryConfigType configType,
        Integer version,
        OffsetDateTime effectiveDate,
        JsonNode payload,
        UUID createdByAdminUserId,
        OffsetDateTime createdAt
) {
}
