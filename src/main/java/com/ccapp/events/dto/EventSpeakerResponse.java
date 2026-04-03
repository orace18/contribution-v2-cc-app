package com.ccapp.events.dto;

import java.util.UUID;

public record EventSpeakerResponse(
        UUID speakerId,
        UUID eventId,
        String name,
        String title,
        String photo
) {
}
