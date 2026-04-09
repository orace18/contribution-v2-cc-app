package com.ccapp.seedcapital.readiness.dto;

import com.ccapp.seedcapital.readiness.domain.enums.CircleMeetingStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CircleMeetingResponse(
        UUID id,
        UUID circleId,
        OffsetDateTime meetingAt,
        CircleMeetingStatus status,
        int participantCount,
        int attendanceCount,
        boolean quorumReached,
        String notes,
        UUID createdByAccountHolderId,
        List<UUID> presentMemberAccountHolderIds,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
