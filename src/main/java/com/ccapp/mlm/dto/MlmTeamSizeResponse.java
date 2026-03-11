package com.ccapp.mlm.dto;

import java.util.UUID;

public record MlmTeamSizeResponse(
        UUID accountHolderId,
        long teamSize
) {
}
