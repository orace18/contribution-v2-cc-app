package com.ccapp.admin.security;

import com.ccapp.admin.domain.enums.AdminRole;
import com.ccapp.common.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class AdminSecurityUtils {

    private AdminSecurityUtils() {
    }

    public static AdminPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AdminPrincipal principal)) {
            throw new UnauthorizedException("Admin authentication required");
        }
        return principal;
    }

    public static UUID currentAdminUserId() {
        return currentPrincipal().adminUserId();
    }

    public static AdminRole currentRole() {
        return currentPrincipal().role();
    }
}
