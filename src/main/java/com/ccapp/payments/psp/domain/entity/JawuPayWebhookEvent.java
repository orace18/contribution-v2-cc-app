package com.ccapp.payments.psp.domain.entity;

import com.ccapp.common.model.AuditableEntity;
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

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "jawupay_webhook_events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_jawupay_webhook_events_event_hash", columnNames = "event_hash"),
                @UniqueConstraint(name = "uk_jawupay_webhook_events_event_id", columnNames = "event_id")
        },
        indexes = {
                @Index(name = "idx_jawupay_webhook_events_operation_id", columnList = "operation_id"),
                @Index(name = "idx_jawupay_webhook_events_payment_id", columnList = "payment_id"),
                @Index(name = "idx_jawupay_webhook_events_payout_id", columnList = "payout_id"),
                @Index(name = "idx_jawupay_webhook_events_provider_reference", columnList = "provider_reference")
        }
)
public class JawuPayWebhookEvent extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", length = 128)
    private String eventId;

    @Column(name = "event_hash", nullable = false, length = 64)
    private String eventHash;

    @Column(name = "operation_id", nullable = false)
    private UUID operationId;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "status", nullable = false, length = 64)
    private String status;

    @Column(name = "payment_id", length = 128)
    private String paymentId;

    @Column(name = "payout_id", length = 128)
    private String payoutId;

    @Column(name = "provider_reference", length = 128)
    private String providerReference;
}
