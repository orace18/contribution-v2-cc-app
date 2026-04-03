package com.ccapp.events.dto;

import com.ccapp.events.domain.enums.EventTicketStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MyEventTicketResponse(
        UUID ticketId,
        UUID eventId,
        String eventTitle,
        OffsetDateTime eventStartDateTime,
        OffsetDateTime eventEndDateTime,
        UUID orderId,
        UUID buyerAccountHolderId,
        UUID ticketTypeId,
        String ticketTypeName,
        String qrCode,
        EventTicketStatus status,
        OffsetDateTime checkinTimestamp
) {
}
