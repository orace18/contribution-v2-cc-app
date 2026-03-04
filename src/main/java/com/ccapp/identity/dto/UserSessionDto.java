package com.ccapp.identity.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserSessionDto(
        UUID id,
        UUID deviceId,
        OffsetDateTime expiresAt
) {
}
