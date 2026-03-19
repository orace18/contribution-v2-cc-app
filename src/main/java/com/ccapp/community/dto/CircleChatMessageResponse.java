package com.ccapp.community.dto;

import com.ccapp.community.domain.enums.CircleChatMessageType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CircleChatMessageResponse(
        UUID id,
        UUID circleId,
        UUID senderId,
        CircleChatMessageType messageType,
        String content,

        /** UUID du fichier dans media_files */
        UUID mediaId,

        /** Presigned URL MinIO générée à la volée (valide ~15 min) */
        String mediaUrl,

        /** Content-type du media (image/jpeg, video/mp4, audio/mpeg...) */
        String mediaContentType,

        Integer mediaDurationSecs,
        String clientMessageId,
        List<ReadReceiptInfo> readBy,
        OffsetDateTime sentAt
) {
    public record ReadReceiptInfo(UUID memberId, OffsetDateTime readAt) {}
}
