package com.ccapp.circles.dto;

import com.ccapp.circles.domain.enums.CircleCycleStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CircleCycleResponse(
        UUID id,
        UUID circleId,
        Integer cycleNumber,
        LocalDate startDate,
        LocalDate endDate,
        CircleCycleStatus status,
        OffsetDateTime createdAt
) {
}
