package com.ccapp.mobile.home.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * @deprecated Legacy mobile home payload retained only for backward compatibility.
 * New mobile work should use {@code MobileHomeDashboardResponse}. The
 * {@code communityHighlights} field is intentionally kept empty so it does not
 * advertise the retired community-feed semantics.
 */
@Deprecated(since = "2026-04", forRemoval = false)
public record MobileHomeResponse(
        MobileHomeMembershipResponse membership,
        BigDecimal rmsBalance,
        List<MobileHomeLoanResponse> activeLoans,
        MobileHomeCircleSummaryResponse circleSummary,
        List<MobileHomeCommunityHighlightResponse> communityHighlights,
        long unreadMessages,
        MobileHomeNotificationSummaryResponse notifications
) {
}
