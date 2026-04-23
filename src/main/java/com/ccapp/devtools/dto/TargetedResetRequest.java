package com.ccapp.devtools.dto;

import com.ccapp.common.exception.BusinessException;

import java.util.UUID;

public record TargetedResetRequest(
        UUID accountHolderId,
        UUID userId
) {
    public void validate() {
        boolean hasAccountHolderId = accountHolderId != null;
        boolean hasUserId = userId != null;
        if (hasAccountHolderId == hasUserId) {
            throw new BusinessException("Provide exactly one of accountHolderId or userId");
        }
    }
}
