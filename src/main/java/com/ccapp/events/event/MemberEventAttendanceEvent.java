package com.ccapp.events.event;

import com.ccapp.common.events.DomainEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MemberEventAttendanceEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID attendedEventId,
        UUID ticketId,
        UUID ticketTypeId,
        UUID accountHolderId,
        OffsetDateTime checkinTimestamp
) implements DomainEvent {

    public static MemberEventAttendanceEvent create(UUID attendedEventId,
                                                    UUID ticketId,
                                                    UUID ticketTypeId,
                                                    UUID accountHolderId,
                                                    OffsetDateTime checkinTimestamp) {
        return new MemberEventAttendanceEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                attendedEventId,
                ticketId,
                ticketTypeId,
                accountHolderId,
                checkinTimestamp
        );
    }
}
