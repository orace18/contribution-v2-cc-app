package com.ccapp.devtools.dto;

import java.util.Set;
import java.util.UUID;

public record DevResidualPurgeRequest(
        UUID accountHolderId,
        Set<UUID> userIds
) {
    public void validate() {
        if (accountHolderId == null) {
            throw new IllegalArgumentException("accountHolderId is required");
        }
    }
}
