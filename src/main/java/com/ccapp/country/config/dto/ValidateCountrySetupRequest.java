package com.ccapp.country.config.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record ValidateCountrySetupRequest(
        @Valid
        @NotNull(message = "payloads is required")
        CountrySetupActivationPayloadsRequest payloads,
        OffsetDateTime effectiveDate
) {
}
