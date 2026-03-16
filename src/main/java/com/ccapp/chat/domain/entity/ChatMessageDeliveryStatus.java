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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
@Table(name = "chat_message_delivery_status",
        uniqueConstraints = @UniqueConstraint(name = "uk_chat_delivery_status_message_recipient",
                columnNames = {"message_id", "recipient_account_holder_id"}),
        indexes = @Index(name = "idx_chat_delivery_status_message", columnList = "message_id"))
public class ChatMessageDeliveryStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "recipient_account_holder_id", nullable = false)
    private UUID recipientAccountHolderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ChatMessageStatus status;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpsert() {
        if (status == null) {
            status = ChatMessageStatus.SENT;
        }
        updatedAt = OffsetDateTime.now();
    }
}
