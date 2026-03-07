package com.ccapp.admin.dto;

import com.ccapp.identity.domain.enums.AccountStatus;
import com.ccapp.identity.domain.enums.KycStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminPlatformUserResponse(
        UUID accountHolderId,
        String firstName,
        String lastName,
        String phoneNumber,
        KycStatus kycStatus,
        AccountStatus accountStatus,
        int appUsersCount,
        OffsetDateTime createdAt
) {
}
