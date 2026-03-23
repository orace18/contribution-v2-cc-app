package com.ccapp.mlm.event;

import com.ccapp.common.events.DomainEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WeeklyBonusCalculatedEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        int weekId,
        int membersProcessed,
        int commissionsCreated,
        int carryoversStored
) implements DomainEvent {

    public static WeeklyBonusCalculatedEvent create(int weekId,
                                                    int membersProcessed,
                                                    int commissionsCreated,
                                                    int carryoversStored) {
        return new WeeklyBonusCalculatedEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                weekId,
                membersProcessed,
                commissionsCreated,
                carryoversStored
        );
    }
}
