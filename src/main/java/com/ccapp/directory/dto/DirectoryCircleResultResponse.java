package com.ccapp.directory.dto;

import java.util.UUID;

public record DirectoryCircleResultResponse(
        UUID circleId,
        String name,
        long membersCount
) {
}
