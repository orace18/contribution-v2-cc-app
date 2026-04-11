package com.ccapp.identity.dto;

import com.ccapp.common.security.SensitiveDataRedactor;
import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank(message = "refreshToken is required")
        String refreshToken
) {
    @Override
    public String toString() {
        return "RefreshTokenRequest[refreshToken=" + SensitiveDataRedactor.REDACTED + "]";
    }
}
