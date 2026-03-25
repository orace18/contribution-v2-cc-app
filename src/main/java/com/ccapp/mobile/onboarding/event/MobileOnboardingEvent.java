package com.ccapp.mobile.onboarding.event;

import com.ccapp.common.events.DomainEvent;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record MobileOnboardingEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID accountHolderId,
        MobileOnboardingEventType eventType,
        Map<String, String> metadata
) implements DomainEvent {

    public static MobileOnboardingEvent create(UUID accountHolderId,
                                               MobileOnboardingEventType eventType,
                                               Map<String, String> metadata) {
        return new MobileOnboardingEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                accountHolderId,
                eventType,
                metadata == null ? Map.of() : Map.copyOf(metadata)
        );
    }
}
