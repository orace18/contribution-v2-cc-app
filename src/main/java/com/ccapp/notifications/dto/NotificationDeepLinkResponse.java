package com.ccapp.notifications.dto;

import java.util.Map;

public record NotificationDeepLinkResponse(
        String routeKey,
        String label,
        Map<String, String> params
) {
}
