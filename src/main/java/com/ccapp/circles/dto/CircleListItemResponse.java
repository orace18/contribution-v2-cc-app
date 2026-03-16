package com.ccapp.circles.dto;

import com.ccapp.circles.domain.enums.CircleMemberRole;
import com.ccapp.circles.domain.enums.CircleStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CircleListItemResponse(
        UUID circleId,
        String name,
        CircleStatus status,
        BigDecimal progress,
        long membersCount,
        CircleMemberRole role,
        long unreadMessages,
        OffsetDateTime lastActivity
) {
}
