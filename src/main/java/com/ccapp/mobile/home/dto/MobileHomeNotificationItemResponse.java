package com.ccapp.mobile.home.dto;

import com.ccapp.notifications.domain.enums.NotificationStatus;
import com.ccapp.notifications.domain.enums.NotificationType;
import com.ccapp.notifications.dto.NotificationDeepLinkResponse;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MobileHomeNotificationItemResponse(
        UUID id,
        NotificationType type,
        String title,
        String message,
        String targetId,
        NotificationDeepLinkResponse deepLink,
        NotificationStatus status,
        OffsetDateTime createdAt
) {
}
