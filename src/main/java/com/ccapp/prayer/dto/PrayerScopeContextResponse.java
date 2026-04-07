package com.ccapp.prayer.dto;

import java.util.UUID;

public record PrayerScopeContextResponse(
        UUID audienceCircleId,
        String audienceChurchRef,
        String audienceCountryCode
) {
}
