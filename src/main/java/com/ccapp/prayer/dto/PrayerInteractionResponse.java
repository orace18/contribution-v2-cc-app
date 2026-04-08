package com.ccapp.prayer.dto;

import com.ccapp.prayer.domain.enums.PrayerInteractionType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PrayerInteractionResponse(
        UUID postId,
        PrayerInteractionType interactionType,
        boolean created,
        int totalCount,
        OffsetDateTime recordedAt,
        PrayerViewerStateResponse viewerState
) {
}
