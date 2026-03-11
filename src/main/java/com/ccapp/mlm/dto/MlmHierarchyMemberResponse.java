package com.ccapp.mlm.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MlmHierarchyMemberResponse(
        UUID accountHolderId,
        Integer level,
        String firstName,
        String lastName,
        OffsetDateTime linkedAt
) {
}
