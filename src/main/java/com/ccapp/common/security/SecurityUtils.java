package com.ccapp.common.security;

import com.ccapp.common.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static AuthenticatedPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
            throw new UnauthorizedException("Authentication required");
        }
        return principal;
    }

    public static UUID currentUserId() {
        AuthenticatedPrincipal principal = currentPrincipal();
        if (principal.userId() == null) {
            throw new UnauthorizedException("User context missing");
        }
        return principal.userId();
    }

    public static UUID currentAccountHolderId() {
        AuthenticatedPrincipal principal = currentPrincipal();
        if (principal.accountHolderId() != null) {
            return principal.accountHolderId();
        }
        if (principal.userId() != null) {
            return principal.userId();
        }
        throw new UnauthorizedException("Account holder context missing");
    }
}
