package com.ccapp.admin.audit;

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
@Table(name = "admin_audit_logs", indexes = {
        @Index(name = "idx_admin_audit_logs_user_timestamp", columnList = "admin_user_id, event_timestamp"),
        @Index(name = "idx_admin_audit_logs_action", columnList = "action")
})
public class AdminAuditLog {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "admin_user_id", nullable = false)
    private UUID adminUserId;

    @Column(name = "action", nullable = false, length = 128)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType;

    @Column(name = "entity_id", length = 64)
    private String entityId;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "event_timestamp", nullable = false)
    private OffsetDateTime timestamp;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (timestamp == null) {
            timestamp = OffsetDateTime.now();
        }
    }
}
