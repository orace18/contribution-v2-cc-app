package com.ccapp.chat.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "chat_conversations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chat_conversations_participants",
                columnNames = {"participant_a_account_holder_id", "participant_b_account_holder_id"}
        ),
        indexes = {
                @Index(name = "idx_chat_conversations_participant_a", columnList = "participant_a_account_holder_id"),
                @Index(name = "idx_chat_conversations_participant_b", columnList = "participant_b_account_holder_id")
        })
public class ChatConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "participant_a_account_holder_id", nullable = false)
    private UUID participantAAccountHolderId;

    @Column(name = "participant_b_account_holder_id", nullable = false)
    private UUID participantBAccountHolderId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
