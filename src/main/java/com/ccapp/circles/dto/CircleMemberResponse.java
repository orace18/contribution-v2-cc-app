package com.ccapp.circles.dto;

import com.ccapp.circles.domain.enums.CircleMemberRole;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CircleMemberResponse(
        UUID id,
        UUID accountHolderId,
        CircleMemberRole role,
        String name,
        UUID profilePhotoMediaId,
        String activity,
        String memberType,
        String managerName,
        String businessActivity,
        String loanStatus,
        BigDecimal repaymentProgress,
        OffsetDateTime joinedAt
) {
}
