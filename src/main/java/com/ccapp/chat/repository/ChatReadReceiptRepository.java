package com.ccapp.chat.repository;

import com.ccapp.chat.domain.entity.ChatReadReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChatReadReceiptRepository extends JpaRepository<ChatReadReceipt, UUID> {
    Optional<ChatReadReceipt> findByMessageIdAndReaderAccountHolderId(UUID messageId, UUID readerAccountHolderId);
}
