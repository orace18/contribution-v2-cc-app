package com.ccapp.events.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.events.domain.enums.EventStoreSyncJobStatus;
import com.ccapp.events.domain.enums.EventStoreSyncJobType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
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
@Table(name = "event_store_sync_jobs", indexes = {
        @Index(name = "idx_event_store_sync_jobs_status_created", columnList = "status, created_at"),
        @Index(name = "idx_event_store_sync_jobs_ticket_type", columnList = "ticket_type_id, created_at")
})
public class EventStoreSyncJob extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ticket_type_id", nullable = false)
    private UUID ticketTypeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private EventStoreSyncJobType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private EventStoreSyncJobStatus status;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @Column(name = "last_error", length = 255)
    private String lastError;
}
