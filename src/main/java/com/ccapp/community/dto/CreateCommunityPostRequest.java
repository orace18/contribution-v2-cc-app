package com.ccapp.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * @deprecated Replaced by Business Prayer admin/mobile post creation requests.
 */
@Deprecated(since = "2026-04", forRemoval = false)
public record CreateCommunityPostRequest(
        @NotBlank(message = "title is required")
        @Size(max = 255, message = "title max size is 255")
        String title,

        @NotBlank(message = "content is required")
        @Size(max = 2000, message = "content max size is 2000")
        String content,

        @Size(max = 512, message = "mediaUrl max size is 512")
        String mediaUrl,

        UUID authorAccountHolderId,

        UUID communityId,

        @Size(max = 32, message = "visibility max size is 32")
        String visibility
) {
}
