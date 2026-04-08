package com.ccapp.prayer.dto;

import com.ccapp.prayer.domain.enums.PrayerDeepLinkRouteKey;

import java.util.Map;

public record InternalDeepLinkResponse(
        PrayerDeepLinkRouteKey routeKey,
        String label,
        Map<String, String> params
) {
}
