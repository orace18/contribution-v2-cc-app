package com.ccapp.prayer.dto;

import com.ccapp.prayer.domain.enums.PrayerDeepLinkRouteKey;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record InternalDeepLinkRequest(
        @NotNull(message = "routeKey is required")
        PrayerDeepLinkRouteKey routeKey,

        String label,

        @NotNull(message = "params is required")
        Map<String, String> params
) {
}
