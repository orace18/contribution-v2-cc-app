package com.ccapp.prayer.dto;

import com.ccapp.prayer.domain.enums.PrayerDailyPrayerResponseType;
import jakarta.validation.constraints.NotNull;

public record CompleteDailyPrayerRequest(
        @NotNull(message = "responseType is required")
        PrayerDailyPrayerResponseType responseType
) {
}
