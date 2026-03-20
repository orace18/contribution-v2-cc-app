package com.ccapp.admin.security;

import com.ccapp.admin.domain.enums.AdminRole;
import com.ccapp.common.exception.UnauthorizedException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
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

    /**
     * Asserts that the current admin is allowed to access the given country.
     * SUPER_ADMIN and OPERATIONS have global access.
     * All other roles (COUNTRY_ADMIN, SUPPORT, IMF_ADMIN) are restricted to their assigned country.
     */
    public static void assertCountryAccess(String countryCode) {
        AdminPrincipal principal = currentPrincipal();
        Set<AdminRole> globalRoles = Set.of(AdminRole.ROLE_SUPER_ADMIN, AdminRole.ROLE_OPERATIONS);
        if (globalRoles.contains(principal.role())) {
            return;
        }
        String assignedCode = principal.countryCode();
        if (assignedCode == null || !assignedCode.equalsIgnoreCase(countryCode)) {
            throw new AccessDeniedException(
                    "Access denied: admin is restricted to country " + assignedCode + ", requested " + countryCode
            );
        }
    }
}
