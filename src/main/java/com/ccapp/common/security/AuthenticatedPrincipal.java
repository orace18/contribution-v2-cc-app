package com.ccapp.common.security;

import lombok.Builder;

import java.util.UUID;

@Builder
public record AuthenticatedPrincipal(
        String firebaseUid,
        String email,
        UUID userId,
        boolean internal
) {
}
