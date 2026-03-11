package com.ccapp.chat.repository;

import com.ccapp.chat.domain.entity.ChatConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, UUID> {

    Optional<ChatConversation> findByParticipantAAccountHolderIdAndParticipantBAccountHolderId(
            UUID participantAAccountHolderId,
            UUID participantBAccountHolderId
    );
}
