package com.ccapp.circles.dto;

import java.util.UUID;

public record ResolveInviteCodeResponse(
        UUID circleId,
        String circleName,
        int membersCount,
        String token
) {
}
