package com.ccapp.admin.security;

import com.ccapp.admin.domain.enums.AdminRole;
import lombok.Builder;

import java.util.UUID;

@Builder
public record AdminPrincipal(
        UUID adminUserId,
        String email,
        AdminRole role,
        UUID countryId
) {
}
