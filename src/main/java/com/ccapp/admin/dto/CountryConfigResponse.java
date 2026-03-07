package com.ccapp.admin.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CountryConfigResponse(
        UUID id,
        String countryCode,
        String name,
        boolean active,
        OffsetDateTime createdAt
) {
}
