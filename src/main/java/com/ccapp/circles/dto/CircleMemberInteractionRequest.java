package com.ccapp.circles.dto;

import com.ccapp.circles.domain.enums.CircleMemberInteractionType;
import jakarta.validation.constraints.NotNull;

public record CircleMemberInteractionRequest(
        @NotNull CircleMemberInteractionType type
) {
}
