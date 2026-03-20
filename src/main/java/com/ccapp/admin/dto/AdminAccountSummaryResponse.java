package com.ccapp.admin.dto;

import com.ccapp.admin.domain.enums.AdminRole;
import com.ccapp.admin.domain.enums.AdminUserStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminAccountSummaryResponse(
        UUID id,
        String email,
        AdminRole role,
        UUID countryId,
        String countryCode,
        AdminUserStatus status,
        boolean twoFactorEnabled,
        OffsetDateTime createdAt,
        OffsetDateTime activatedAt
) {
}
