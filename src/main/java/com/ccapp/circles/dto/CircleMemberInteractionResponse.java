package com.ccapp.circles.dto;

import com.ccapp.circles.domain.enums.CircleMemberInteractionType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CircleMemberInteractionResponse(
        UUID id,
        UUID circleId,
        UUID memberAccountHolderId,
        UUID senderAccountHolderId,
        CircleMemberInteractionType type,
        OffsetDateTime createdAt
) {
}
