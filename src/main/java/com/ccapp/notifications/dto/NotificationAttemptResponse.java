package com.ccapp.notifications.dto;

import com.ccapp.notifications.domain.enums.NotificationChannel;
import com.ccapp.notifications.domain.enums.NotificationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationAttemptResponse(
        UUID id,
        NotificationChannel channel,
        Integer attemptNumber,
        NotificationStatus status,
        String providerResponse,
        OffsetDateTime createdAt
) {
}
