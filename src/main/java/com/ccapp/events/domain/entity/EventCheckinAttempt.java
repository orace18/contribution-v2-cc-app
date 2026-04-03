package com.ccapp.events.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.events.domain.enums.EventCheckinResult;
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
@Table(name = "event_checkin_attempts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_event_checkin_attempts_client_scan", columnNames = "client_scan_id")
}, indexes = {
        @Index(name = "idx_event_checkin_attempts_ticket_processed", columnList = "ticket_id, processed_at"),
        @Index(name = "idx_event_checkin_attempts_qr_processed", columnList = "qr_hash, processed_at")
})
public class EventCheckinAttempt extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_scan_id", length = 64)
    private String clientScanId;

    @Column(name = "source_device_id", length = 128)
    private String sourceDeviceId;

    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "ticket_id")
    private UUID ticketId;

    @Column(name = "qr_hash", nullable = false, length = 128)
    private String qrHash;

    @Column(name = "client_scanned_at")
    private OffsetDateTime clientScannedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 16)
    private EventCheckinResult result;

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;
}
