package com.ccapp.directory.dto;

import java.util.UUID;

public record DirectoryMemberResultResponse(
        UUID accountHolderId,
        String name,
        String activity,
        String avatarUrl
) {
}
