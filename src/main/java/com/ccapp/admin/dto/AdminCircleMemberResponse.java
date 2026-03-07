package com.ccapp.admin.dto;

import com.ccapp.circles.domain.enums.CircleMemberRole;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminCircleMemberResponse(
        UUID accountHolderId,
        CircleMemberRole role,
        String businessActivity,
        String loanStatus,
        BigDecimal repaymentProgress,
        OffsetDateTime joinedAt
) {
}
