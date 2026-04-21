package com.ccapp.seedcapital.readiness.dto;

import com.ccapp.seedcapital.readiness.domain.enums.CircleMeetingStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateCircleMeetingStatusRequest(
        @NotNull CircleMeetingStatus status
) {
}
