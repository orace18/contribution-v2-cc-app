package com.ccapp.community.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * @deprecated Legacy community featuring request replaced by official Business Prayer publication flows.
 */
@Deprecated(since = "2026-04", forRemoval = false)
public record FeatureCommunityPostRequest(
        @NotNull(message = "priority is required")
        @Positive(message = "priority must be > 0")
        Integer priority
) {
}
