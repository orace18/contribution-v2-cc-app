package com.ccapp.country.config.dto;

import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

public record CountryConfigSnapshotItemResponse(
        CountryConfigType configType,
        Integer version,
        OffsetDateTime effectiveDate,
        JsonNode payload
) {
}
