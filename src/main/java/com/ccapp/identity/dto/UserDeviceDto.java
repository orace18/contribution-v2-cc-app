package com.ccapp.identity.dto;

import com.ccapp.identity.domain.enums.Platform;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserDeviceDto(
        UUID id,
        String deviceIdentifier,
        Platform platform,
        boolean blocked,
        OffsetDateTime lastSeenAt
) {
}
