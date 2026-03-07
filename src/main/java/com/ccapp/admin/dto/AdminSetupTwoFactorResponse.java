package com.ccapp.admin.dto;

public record AdminSetupTwoFactorResponse(
        boolean twoFactorEnabled,
        String secret,
        String otpAuthUri,
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {
}
