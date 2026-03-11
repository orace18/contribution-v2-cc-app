package com.ccapp.chat.domain.entity;

import com.ccapp.chat.domain.enums.ChatMessageStatus;
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
@Table(name = "chat_messages",
        uniqueConstraints = @UniqueConstraint(name = "uk_chat_messages_sender_client_message",
                columnNames = {"sender_account_holder_id", "client_message_id"}),
        indexes = {
                @Index(name = "idx_chat_messages_sender_created", columnList = "sender_account_holder_id, created_at"),
                @Index(name = "idx_chat_messages_recipient_created", columnList = "recipient_account_holder_id, created_at"),
                @Index(name = "idx_chat_messages_conversation_created", columnList = "conversation_id, created_at"),
                @Index(name = "idx_chat_messages_recipient_status_created", columnList = "recipient_account_holder_id, message_status, created_at")
        })
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sender_account_holder_id", nullable = false)
    private UUID senderAccountHolderId;

    @Column(name = "recipient_account_holder_id", nullable = false)
    private UUID recipientAccountHolderId;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "content", nullable = false, length = 2000)
    private String content;

    @Column(name = "channel", nullable = false, length = 32)
    private String channel;

    @Column(name = "client_message_id", length = 128)
    private String clientMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_status", nullable = false, length = 16)
    private ChatMessageStatus messageStatus;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (messageStatus == null) {
            messageStatus = ChatMessageStatus.SENT;
        }
    }
}
