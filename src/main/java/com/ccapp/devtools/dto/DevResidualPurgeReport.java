package com.ccapp.devtools.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DevResidualPurgeReport(
        UUID accountHolderId,
        String finalStatus,
        OffsetDateTime purgedAt,
        List<DevChecklistGroup> before,
        List<DevChecklistGroup> after,
        List<String> skipped,
        List<String> warnings
) {
}
