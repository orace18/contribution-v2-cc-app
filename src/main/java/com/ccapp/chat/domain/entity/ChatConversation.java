package com.ccapp.chat.domain.entity;

import com.ccapp.chat.domain.enums.ChatConversationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
                @Index(name = "idx_chat_conversations_participant_b", columnList = "participant_b_account_holder_id"),
                @Index(name = "idx_chat_conversations_circle", columnList = "circle_id")
        })
public class ChatConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    @Builder.Default
    private ChatConversationType type = ChatConversationType.DIRECT;

    @Column(name = "participant_a_account_holder_id")
    private UUID participantAAccountHolderId;

    @Column(name = "participant_b_account_holder_id")
    private UUID participantBAccountHolderId;

    @Column(name = "circle_id")
    private UUID circleId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (type == null) {
            type = ChatConversationType.DIRECT;
        }
    }
}
