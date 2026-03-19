package com.ccapp.community.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "circle_chat_read_receipts",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_circle_chat_read",
                columnNames = {"message_id", "member_id"}
        ),
        indexes = {
                @Index(name = "idx_circle_chat_read_message", columnList = "message_id"),
                @Index(name = "idx_circle_chat_read_member", columnList = "member_id")
        })
public class CircleChatReadReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "read_at", nullable = false)
    private OffsetDateTime readAt;

    @PrePersist
    protected void onCreate() {
        if (this.readAt == null) {
            this.readAt = OffsetDateTime.now();
        }
    }
}
