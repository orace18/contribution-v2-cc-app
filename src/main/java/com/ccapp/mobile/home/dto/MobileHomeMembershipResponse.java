package com.ccapp.mobile.home.dto;

import java.time.OffsetDateTime;

public record MobileHomeMembershipResponse(
        String status,
        OffsetDateTime lastSubscriptionAt,
        OffsetDateTime expiresAt,
        Long daysRemaining
) {
}
