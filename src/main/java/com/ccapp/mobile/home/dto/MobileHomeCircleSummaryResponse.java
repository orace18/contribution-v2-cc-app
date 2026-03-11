package com.ccapp.mobile.home.dto;

import com.ccapp.circles.domain.enums.CircleStatus;

import java.util.UUID;

public record MobileHomeCircleSummaryResponse(
        UUID circleId,
        String name,
        CircleStatus status,
        long membersCount
) {
}
