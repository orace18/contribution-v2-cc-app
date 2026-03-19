package com.ccapp.community.domain.entity;

import com.ccapp.community.domain.enums.CircleChatMessageType;
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
@Table(name = "circle_chat_messages",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_circle_chat_client_msg",
                columnNames = {"circle_id", "sender_id", "client_message_id"}
        ),
        indexes = {
                @Index(name = "idx_circle_chat_circle_sent", columnList = "circle_id, sent_at DESC"),
                @Index(name = "idx_circle_chat_sender", columnList = "sender_id")
        })
public class CircleChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "circle_id", nullable = false)
    private UUID circleId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 16)
    @Builder.Default
    private CircleChatMessageType messageType = CircleChatMessageType.TEXT;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /** Référence vers la table media_files (fichier uploadé sur MinIO) */
    @Column(name = "media_id")
    private UUID mediaId;

    /** Durée en secondes pour audio/vidéo — fourni par le client */
    @Column(name = "media_duration_secs")
    private Integer mediaDurationSecs;

    @Column(name = "client_message_id", length = 128)
    private String clientMessageId;

    @Column(name = "sent_at", nullable = false)
    private OffsetDateTime sentAt;

    @PrePersist
    protected void onCreate() {
        this.sentAt = OffsetDateTime.now();
    }
}
