package com.ccapp.chat.repository;

import com.ccapp.chat.domain.entity.ChatMessageDeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChatMessageDeliveryStatusRepository extends JpaRepository<ChatMessageDeliveryStatus, UUID> {
    Optional<ChatMessageDeliveryStatus> findByMessageIdAndRecipientAccountHolderId(UUID messageId, UUID recipientAccountHolderId);
}
