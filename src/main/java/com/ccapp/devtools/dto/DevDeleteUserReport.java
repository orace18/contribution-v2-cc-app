package com.ccapp.devtools.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Enriched response for POST /dev-tools/delete-user.
 *
 * status: CLEAN (deleted, no residuals) | PARTIAL (deleted, residuals remain) | NOT_FOUND (target not resolved)
 * userIds: all linked user IDs — pass to /dev-tools/purge-residuals if residualDataDetected=true
 */
public record DevDeleteUserReport(
        String status,
        boolean deleted,
        UUID accountHolderId,
        UUID userId,
        Set<UUID> userIds,
        String email,
        OffsetDateTime verifiedAt,
        List<DevChecklistGroup> verificationChecklist,
        boolean residualDataDetected
) {
}
