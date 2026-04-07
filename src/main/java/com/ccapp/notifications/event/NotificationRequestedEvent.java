package com.ccapp.notifications.event;

import com.ccapp.common.events.DomainEvent;
import com.ccapp.notifications.dto.NotificationDeepLinkRequest;
import com.ccapp.notifications.domain.enums.NotificationChannel;
import com.ccapp.notifications.domain.enums.NotificationType;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record NotificationRequestedEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID accountHolderId,
        UUID actorUserId,
        NotificationType type,
        NotificationChannel channel,
        String templateCode,
        String message,
        Map<String, String> variables,
        String targetId,
        NotificationDeepLinkRequest deepLink,
        Boolean sendNow
) implements DomainEvent {

    public static NotificationRequestedEvent create(UUID accountHolderId,
                                                    UUID actorUserId,
                                                    NotificationType type,
                                                    NotificationChannel channel,
                                                    String templateCode,
                                                    String message,
                                                    Map<String, String> variables,
                                                    Boolean sendNow) {
        return new NotificationRequestedEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                accountHolderId,
                actorUserId,
                type,
                channel,
                templateCode,
                message,
                variables,
                null,
                null,
                sendNow
        );
    }
}
