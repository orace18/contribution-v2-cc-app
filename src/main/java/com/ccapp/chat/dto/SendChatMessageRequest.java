package com.ccapp.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SendChatMessageRequest(
        UUID conversationId,

        UUID recipientAccountHolderId,

        @NotBlank(message = "content is required")
        @Size(max = 2000, message = "content max size is 2000")
        String content,

        @Size(max = 32, message = "channel max size is 32")
        String channel,

        @Size(max = 128, message = "clientMessageId max size is 128")
        String clientMessageId
) {
}
