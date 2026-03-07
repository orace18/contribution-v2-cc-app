package com.ccapp.admin.dto;

import com.ccapp.admin.domain.enums.AdminInvitationStatus;
import com.ccapp.admin.domain.enums.AdminRole;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminInvitationResponse(
        UUID id,
        String email,
        AdminRole role,
        UUID countryId,
        AdminInvitationStatus status,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt
) {
}
