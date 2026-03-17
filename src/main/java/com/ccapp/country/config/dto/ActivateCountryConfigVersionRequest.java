package com.ccapp.country.config.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record ActivateCountryConfigVersionRequest(
        @NotNull(message = "version is required")
        Integer version,
        OffsetDateTime effectiveDate
) {
}
