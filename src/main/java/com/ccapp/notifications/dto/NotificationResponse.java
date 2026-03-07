package com.ccapp.notifications.dto;

import com.ccapp.notifications.domain.enums.NotificationChannel;
import com.ccapp.notifications.domain.enums.NotificationStatus;
import com.ccapp.notifications.domain.enums.NotificationType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID accountHolderId,
        UUID actorUserId,
        NotificationType type,
        NotificationChannel channel,
        String templateCode,
        String message,
        NotificationStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime sentAt,
        List<NotificationAttemptResponse> attempts
) {
}
