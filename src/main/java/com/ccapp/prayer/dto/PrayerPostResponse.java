package com.ccapp.prayer.dto;

import com.ccapp.prayer.domain.enums.PrayerPostLifecycleStatus;
import com.ccapp.prayer.domain.enums.PrayerPostType;
import com.ccapp.prayer.domain.enums.PrayerVisibilityScope;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PrayerPostResponse(
        UUID id,
        PrayerPostType postType,
        PrayerPostLifecycleStatus lifecycleStatus,
        String title,
        String content,
        PrayerVisibilityScope visibility,
        PrayerAuthorResponse author,
        PrayerScopeContextResponse scopeContext,
        List<PrayerMediaRefResponse> mediaRefs,
        InternalDeepLinkResponse deepLink,
        PrayerStatsResponse stats,
        PrayerViewerStateResponse viewerState,
        OffsetDateTime createdAt,
        OffsetDateTime publishedAt
) {
}
