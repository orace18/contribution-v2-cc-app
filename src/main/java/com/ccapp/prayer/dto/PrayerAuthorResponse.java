package com.ccapp.prayer.dto;

import com.ccapp.prayer.domain.enums.PrayerAuthorType;

import java.util.UUID;

public record PrayerAuthorResponse(
        UUID accountHolderId,
        String displayName,
        PrayerAuthorType authorType,
        UUID avatarMediaId
) {
}
