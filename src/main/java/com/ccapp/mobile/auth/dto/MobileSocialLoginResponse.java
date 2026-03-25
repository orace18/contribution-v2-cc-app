package com.ccapp.mobile.auth.dto;

import com.ccapp.identity.domain.enums.AccountStatus;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.domain.enums.UserRole;

import java.util.UUID;

public record MobileSocialLoginResponse(
        UUID userId,
        UUID accountHolderId,
        String firebaseUid,
        String email,
        UserRole role,
        AccountStatus accountStatus,
        KycStatus kycStatus,
        UUID sessionId,
        String refreshToken
) {
}
