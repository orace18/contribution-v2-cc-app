package com.ccapp.progress.event;

import com.ccapp.common.events.DomainEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FriendInvitedEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID accountHolderId,
        UUID circleId,
        UUID inviteId
) implements DomainEvent {

    public static FriendInvitedEvent create(UUID accountHolderId, UUID circleId, UUID inviteId) {
        return new FriendInvitedEvent(UUID.randomUUID(), OffsetDateTime.now(), accountHolderId, circleId, inviteId);
    }
}
