package com.ccapp.mlm.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MlmPlacementResponse(
        UUID accountHolderId,
        UUID sponsorAccountHolderId,
        UUID placedUnderAccountHolderId,
        long directChildrenCount,
        long sponsorTeamSize,
        OffsetDateTime placedAt
) {
}
