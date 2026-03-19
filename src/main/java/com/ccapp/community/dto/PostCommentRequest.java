package com.ccapp.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record PostCommentRequest(
        @NotBlank(message = "content is required")
        @Size(max = 1000, message = "content max size is 1000")
        String content,

        UUID parentCommentId
) {
}
