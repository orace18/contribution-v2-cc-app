package com.ccapp.prayer.dto;

import com.ccapp.prayer.domain.enums.PrayerHomeSuggestionSourceType;
import com.ccapp.prayer.domain.enums.PrayerSuggestionAudioStatus;
import com.ccapp.prayer.domain.enums.PrayerSuggestionCategory;

import java.util.List;
import java.util.UUID;

public record PrayerHomeSuggestionResponse(
        UUID referenceId,
        PrayerHomeSuggestionSourceType sourceType,
        String title,
        String content,
        PrayerSuggestionCategory category,
        List<String> tags,
        String selectionReason,
        PrayerSuggestionAudioStatus audioStatus,
        String audioUrl,
        InternalDeepLinkResponse deepLink
) {
}
