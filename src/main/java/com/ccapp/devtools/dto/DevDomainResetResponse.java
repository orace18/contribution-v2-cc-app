package com.ccapp.devtools.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DevDomainResetResponse(
        boolean applied,
        String domain,
        UUID accountHolderId,
        UUID userId,
        OffsetDateTime resetAt,
        String message,
        Map<String, Object> summary,
        List<String> warnings
) {
}
