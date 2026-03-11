package com.ccapp.common.events;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface DomainEvent {
    UUID eventId();

    OffsetDateTime occurredAt();
}
