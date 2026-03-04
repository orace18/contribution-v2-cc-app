package com.ccapp.identity.dto;

import java.util.UUID;

public record RefreshTokenResponse(
        UUID sessionId,
        String refreshToken
) {
}
