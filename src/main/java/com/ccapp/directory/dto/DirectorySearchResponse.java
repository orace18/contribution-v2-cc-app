package com.ccapp.directory.dto;

import java.util.List;

public record DirectorySearchResponse(
        List<DirectoryCircleResultResponse> circles,
        List<DirectoryMemberResultResponse> members
) {
}
