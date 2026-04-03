package com.ccapp.events.dto;

import com.ccapp.events.domain.enums.EventLocationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CreateEventRequest(
        @NotBlank(message = "title is required")
        @Size(max = 255, message = "title max size is 255")
        String title,

        String description,

        @NotBlank(message = "country is required")
        @Size(min = 2, max = 3, message = "country must have 2 or 3 characters")
        String country,

        @NotNull(message = "startDateTime is required")
        OffsetDateTime startDateTime,

        @NotNull(message = "endDateTime is required")
        OffsetDateTime endDateTime,

        @NotNull(message = "locationType is required")
        EventLocationType locationType,

        @Size(max = 512, message = "locationAddress max size is 512")
        String locationAddress,

        @Size(max = 512, message = "videoLink max size is 512")
        String videoLink,

        UUID organizerAccountHolderId,

        @Valid
        List<EventSpeakerRequest> speakers,

        List<UUID> controlAgentAccountHolderIds
) {
}
