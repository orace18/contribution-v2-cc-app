package com.ccapp.identity.managedaccounts.dto;

import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.domain.enums.KycLevel;
import com.ccapp.identity.domain.enums.KycStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ManagedAccountSummaryResponse(
        UUID accountHolderId,
        String displayName,
        String phoneNumber,
        String countryCode,
        String serviceCountryCode,
        AccountType accountType,
        KycStatus kycStatus,
        KycLevel kycLevel,
        boolean phoneVerified,
        PermissionLevel managerPermissionLevel,
        long unreadNotificationCount,
        OffsetDateTime managedSince,
        OffsetDateTime createdAt
) {
}
