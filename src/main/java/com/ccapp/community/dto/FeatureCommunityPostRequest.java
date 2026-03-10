package com.ccapp.community.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record FeatureCommunityPostRequest(
        @NotNull(message = "priority is required")
        @Positive(message = "priority must be > 0")
        Integer priority
) {
}
