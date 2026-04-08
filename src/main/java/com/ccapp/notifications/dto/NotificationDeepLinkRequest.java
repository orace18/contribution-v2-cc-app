package com.ccapp.notifications.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record NotificationDeepLinkRequest(
        @NotBlank(message = "deepLink.routeKey is required")
        @Size(max = 64, message = "deepLink.routeKey max size is 64")
        String routeKey,

        @Size(max = 160, message = "deepLink.label max size is 160")
        String label,

        @NotNull(message = "deepLink.params is required")
        Map<String, String> params
) {
}
