package com.ccapp.payments.psp.domain.entity;

import com.ccapp.common.model.AuditableEntity;
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
@Table(name = "psp_callback_events", indexes = {
        @Index(name = "idx_psp_callback_events_received_at", columnList = "received_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_psp_callback_nonce", columnNames = "nonce")
})
public class PspCallbackEvent extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "nonce", nullable = false, length = 128)
    private String nonce;

    @Column(name = "signature", nullable = false, length = 256)
    private String signature;

    @Column(name = "payload_hash", nullable = false, length = 256)
    private String payloadHash;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;
}
