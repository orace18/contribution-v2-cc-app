package com.ccapp.circles.dto;

import com.ccapp.circles.domain.enums.CircleMemberRole;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CircleMemberProfileResponse(
        UUID accountHolderId,
        String name,
        String profilePicture,
        String activity,
        OffsetDateTime joinDate,
        CircleMemberRole role,
        String status
) {
}
