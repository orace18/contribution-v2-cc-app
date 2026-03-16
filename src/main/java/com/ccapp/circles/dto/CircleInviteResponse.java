package com.ccapp.circles.dto;

import com.ccapp.circles.domain.enums.CircleInviteStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CircleInviteResponse(
        UUID id,
        UUID circleId,
        CircleInviteStatus status,
        String invitationToken,
        String inviteLink,
        int maxUses,
        int usedCount,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt
) {
}
