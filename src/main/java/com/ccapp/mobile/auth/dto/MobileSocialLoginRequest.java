package com.ccapp.mobile.auth.dto;

import com.ccapp.identity.domain.enums.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MobileSocialLoginRequest(
        @NotBlank(message = "deviceIdentifier is required")
        String deviceIdentifier,

        @NotNull(message = "platform is required")
        Platform platform,

        String idToken
) {
}
