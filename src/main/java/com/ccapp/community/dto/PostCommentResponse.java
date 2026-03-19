package com.ccapp.community.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PostCommentResponse(
        UUID id,
        UUID postId,
        UUID accountHolderId,
        String content,
        UUID parentCommentId,
        List<PostCommentResponse> replies,
        OffsetDateTime createdAt
) {
}
