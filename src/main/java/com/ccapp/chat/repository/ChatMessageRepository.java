package com.ccapp.chat.repository;

import com.ccapp.chat.domain.entity.ChatMessage;
import com.ccapp.chat.domain.enums.ChatMessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    Optional<ChatMessage> findBySenderAccountHolderIdAndClientMessageId(UUID senderAccountHolderId, String clientMessageId);

    Page<ChatMessage> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

    Page<ChatMessage> findByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(UUID conversationId,
                                                                                  OffsetDateTime before,
                                                                                  Pageable pageable);

    long countByRecipientAccountHolderIdAndMessageStatusIn(UUID recipientAccountHolderId,
                                                           Collection<ChatMessageStatus> statuses);
}
