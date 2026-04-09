package com.ccapp.seedcapital.readiness.dto;

import com.ccapp.seedcapital.readiness.domain.enums.CircleMeetingStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RecordCircleMeetingRequest(
        @NotNull OffsetDateTime meetingAt,
        CircleMeetingStatus status,
        List<UUID> presentMemberAccountHolderIds,
        @Size(max = 1000) String notes
) {
}
