package com.ccapp.admin.dto;

import com.ccapp.admin.domain.enums.AdminRole;
import com.ccapp.admin.domain.enums.AdminUserStatus;

import java.util.UUID;

public record AdminAcceptInvitationResponse(
        UUID adminUserId,
        String email,
        AdminRole role,
        AdminUserStatus status,
        boolean requiresTwoFactorSetup
) {
}
