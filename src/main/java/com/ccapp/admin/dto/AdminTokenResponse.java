package com.ccapp.admin.dto;

public record AdminTokenResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {
}
