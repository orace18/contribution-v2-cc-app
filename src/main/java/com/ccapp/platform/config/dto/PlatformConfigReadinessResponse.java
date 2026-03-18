package com.ccapp.platform.config.dto;

import java.util.List;

public record PlatformConfigReadinessResponse(
        boolean ready,
        int score,
        List<String> missing
) {
}
