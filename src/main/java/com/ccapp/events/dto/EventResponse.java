package com.ccapp.events.dto;

import com.ccapp.events.domain.enums.EventLocationType;
import com.ccapp.events.domain.enums.EventStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record EventResponse(
        UUID eventId,
        String title,
        String description,
        String country,
        OffsetDateTime startDateTime,
        OffsetDateTime endDateTime,
        EventLocationType locationType,
        String locationAddress,
        String videoLink,
        UUID organizerAccountHolderId,
        EventStatus status,
        List<EventSpeakerResponse> speakers,
        List<EventTicketTypeResponse> ticketTypes,
        List<UUID> controlAgentAccountHolderIds
) {
}
