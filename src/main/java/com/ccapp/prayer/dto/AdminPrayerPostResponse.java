package com.ccapp.prayer.dto;

import com.ccapp.prayer.domain.enums.PrayerOfficialVisibilityScope;
import com.ccapp.prayer.domain.enums.PrayerPostLifecycleStatus;
import com.ccapp.prayer.domain.enums.PrayerPostType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminPrayerPostResponse(
        UUID id,
        PrayerPostType postType,
        PrayerPostLifecycleStatus lifecycleStatus,
        String title,
        String content,
        PrayerOfficialVisibilityScope visibility,
        String targetCountryCode,
        String targetChurchRef,
        List<PrayerMediaRefResponse> mediaRefs,
        InternalDeepLinkResponse deepLink,
        UUID createdByAdminUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime publishedAt
) {
}
