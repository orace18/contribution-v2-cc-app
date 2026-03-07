package com.ccapp.notifications.dto;

import com.ccapp.notifications.domain.enums.NotificationChannel;
import com.ccapp.notifications.domain.enums.NotificationType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SendMultiChannelNotificationRequest(
        @NotNull(message = "accountHolderId is required")
        UUID accountHolderId,

        UUID actorUserId,

        @NotNull(message = "type is required")
        NotificationType type,

        @NotEmpty(message = "channels are required")
        List<NotificationChannel> channels,

        @Size(max = 64, message = "templateCode max size is 64")
        String templateCode,

        @Size(max = 2000, message = "message max size is 2000")
        String message,

        Map<String, String> variables,

        Boolean sendNow
) {
}
