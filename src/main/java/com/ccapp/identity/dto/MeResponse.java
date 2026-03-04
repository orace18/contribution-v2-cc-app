package com.ccapp.identity.dto;

import com.ccapp.identity.domain.enums.AccountStatus;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.domain.enums.UserRole;

import java.util.List;
import java.util.UUID;

public record MeResponse(
        UUID id,
        String firebaseUid,
        String email,
        UserRole role,
        AccountStatus accountStatus,
        KycStatus kycStatus,
        UserProfileDto profile,
        List<UserDeviceDto> devices,
        List<UserSessionDto> activeSessions
) {
}
