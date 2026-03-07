package com.ccapp.admin.dto;

import com.ccapp.admin.domain.enums.AdminRole;
import com.ccapp.admin.domain.enums.AdminUserStatus;

public record AdminLoginResponse(
        String challengeToken,
        boolean requiresTwoFactor,
        boolean requiresTwoFactorSetup,
        AdminRole role,
        AdminUserStatus status
) {
}
