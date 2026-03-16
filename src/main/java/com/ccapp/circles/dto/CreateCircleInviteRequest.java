package com.ccapp.circles.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CreateCircleInviteRequest(
        @Min(value = 1, message = "maxUses must be >= 1")
        @Max(value = 5, message = "maxUses must be <= 5")
        Integer maxUses,

        @Min(value = 1, message = "expiresInHours must be >= 1")
        @Max(value = 240, message = "expiresInHours must be <= 240")
        Integer expiresInHours
) {
}
