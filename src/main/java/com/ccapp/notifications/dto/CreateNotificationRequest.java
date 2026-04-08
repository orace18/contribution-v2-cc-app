package com.ccapp.notifications.dto;

import com.ccapp.notifications.domain.enums.NotificationChannel;
import com.ccapp.notifications.domain.enums.NotificationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

public record CreateNotificationRequest(
        @NotNull(message = "accountHolderId is required")
        UUID accountHolderId,

        UUID actorUserId,

        @NotNull(message = "type is required")
        NotificationType type,

        @NotNull(message = "channel is required")
        NotificationChannel channel,

        @Size(max = 64, message = "templateCode max size is 64")
        String templateCode,

        @Size(max = 255, message = "title max size is 255")
        String title,

        @Size(max = 2000, message = "message max size is 2000")
        String message,

        Map<String, String> variables,

        @Size(max = 128, message = "targetId max size is 128")
        String targetId,

        @Valid
        NotificationDeepLinkRequest deepLink,

        Boolean sendNow
) {
}
