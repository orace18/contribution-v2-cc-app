package com.ccapp.mobile.home.dto;

import com.ccapp.notifications.domain.enums.NotificationStatus;
import com.ccapp.notifications.domain.enums.NotificationType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MobileHomeNotificationItemResponse(
        UUID id,
        NotificationType type,
        String title,
        String message,
        NotificationStatus status,
        OffsetDateTime createdAt
) {
}
