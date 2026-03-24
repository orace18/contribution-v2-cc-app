package com.ccapp.chat.repository;

import com.ccapp.chat.domain.entity.ChatConversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, UUID> {

    Optional<ChatConversation> findByCircleId(UUID circleId);

    Optional<ChatConversation> findByParticipantAAccountHolderIdAndParticipantBAccountHolderId(
            UUID participantAAccountHolderId,
            UUID participantBAccountHolderId
    );

    @Query("""
            select c from ChatConversation c
            where c.participantAAccountHolderId = :accountHolderId
               or c.participantBAccountHolderId = :accountHolderId
            """)
    Page<ChatConversation> findByParticipantAccountHolderId(UUID accountHolderId, Pageable pageable);

    @Query(value = """
            SELECT CAST(c.id AS VARCHAR) AS conversationId,
                   CAST(c.participant_a_account_holder_id AS VARCHAR) AS participantAAccountHolderId,
                   CAST(c.participant_b_account_holder_id AS VARCHAR) AS participantBAccountHolderId,
                   COALESCE(MAX(m.created_at), c.created_at) AS lastActivity
            FROM chat_conversations c
            LEFT JOIN chat_messages m
              ON m.conversation_id = c.id
            WHERE c.participant_a_account_holder_id = :accountHolderId
               OR c.participant_b_account_holder_id = :accountHolderId
            GROUP BY c.id, c.participant_a_account_holder_id, c.participant_b_account_holder_id, c.created_at
            ORDER BY lastActivity DESC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM chat_conversations c
                    WHERE c.participant_a_account_holder_id = :accountHolderId
                       OR c.participant_b_account_holder_id = :accountHolderId
                    """,
            nativeQuery = true)
    Page<ConversationSummaryProjection> findConversationSummariesByParticipant(UUID accountHolderId, Pageable pageable);

    interface ConversationSummaryProjection {
        String getConversationId();

        String getParticipantAAccountHolderId();

        String getParticipantBAccountHolderId();

        OffsetDateTime getLastActivity();
    }
}
