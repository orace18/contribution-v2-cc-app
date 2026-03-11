package com.ccapp.circlesocial.event;

import com.ccapp.circlesocial.domain.enums.ActivityType;
import com.ccapp.common.events.DomainEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CircleActivityRequestedEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID circleId,
        UUID actorUserId,
        ActivityType activityType,
        UUID referenceId,
        String message
) implements DomainEvent {

    public static CircleActivityRequestedEvent create(UUID circleId,
                                                      UUID actorUserId,
                                                      ActivityType activityType,
                                                      UUID referenceId,
                                                      String message) {
        return new CircleActivityRequestedEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                circleId,
                actorUserId,
                activityType,
                referenceId,
                message
        );
    }
}
