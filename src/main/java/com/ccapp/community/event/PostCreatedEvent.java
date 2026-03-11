package com.ccapp.community.event;

import com.ccapp.common.events.DomainEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PostCreatedEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID postId,
        UUID authorAccountHolderId,
        UUID communityId,
        String visibility
) implements DomainEvent {

    public static PostCreatedEvent create(UUID postId,
                                          UUID authorAccountHolderId,
                                          UUID communityId,
                                          String visibility) {
        return new PostCreatedEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                postId,
                authorAccountHolderId,
                communityId,
                visibility
        );
    }
}
