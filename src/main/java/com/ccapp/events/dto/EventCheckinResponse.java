package com.ccapp.events.dto;

import com.ccapp.events.domain.enums.EventCheckinResult;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EventCheckinResponse(
        EventCheckinResult result,
        UUID ticketId,
        UUID eventId,
        OffsetDateTime checkinTimestamp
) {
}
