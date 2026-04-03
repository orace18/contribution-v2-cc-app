package com.ccapp.events.dto;

import com.ccapp.events.domain.enums.EventCheckinResult;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EventBatchCheckinItemResponse(
        String clientScanId,
        EventCheckinResult result,
        UUID ticketId,
        UUID eventId,
        OffsetDateTime checkinTimestamp
) {
}
