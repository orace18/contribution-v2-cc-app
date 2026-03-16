package com.ccapp.chat.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
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
@Table(name = "chat_read_receipts",
        uniqueConstraints = @UniqueConstraint(name = "uk_chat_read_receipts_message_reader",
                columnNames = {"message_id", "reader_account_holder_id"}),
        indexes = @Index(name = "idx_chat_read_receipts_message", columnList = "message_id"))
public class ChatReadReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "reader_account_holder_id", nullable = false)
    private UUID readerAccountHolderId;

    @Column(name = "read_at", nullable = false)
    private OffsetDateTime readAt;

    @PrePersist
    protected void onCreate() {
        if (readAt == null) {
            readAt = OffsetDateTime.now();
        }
    }
}
