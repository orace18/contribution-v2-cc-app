package com.ccapp.circles.dto;

import com.ccapp.circles.domain.enums.CircleMeetingFrequency;
import jakarta.validation.constraints.NotNull;

public record UpdateCircleMeetingPolicyRequest(
        @NotNull(message = "frequency is required")
        CircleMeetingFrequency frequency
) {
}
