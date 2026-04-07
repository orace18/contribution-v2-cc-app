package com.ccapp.prayer.dto;

import com.ccapp.prayer.domain.enums.PrayerMediaUsageType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PrayerMediaAttachmentInput(
        @NotNull(message = "mediaFileId is required")
        UUID mediaFileId,

        @NotNull(message = "usageType is required")
        PrayerMediaUsageType usageType,

        @Min(value = 0, message = "sortOrder must be >= 0")
        Integer sortOrder
) {
}
