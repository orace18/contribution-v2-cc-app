package com.ccapp.mobile.home.dto;

import java.math.BigDecimal;
import java.util.List;

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
