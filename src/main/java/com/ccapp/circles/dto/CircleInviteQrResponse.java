package com.ccapp.circles.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CircleInviteQrResponse(
        UUID inviteId,
        String qrPayload,
        String inviteLink,
        OffsetDateTime expiresAt
) {
}
