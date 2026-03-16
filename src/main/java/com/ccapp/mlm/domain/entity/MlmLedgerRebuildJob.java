package com.ccapp.mlm.domain.entity;

import com.ccapp.mlm.domain.enums.MlmLedgerRebuildJobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
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
@Table(name = "mlm_ledger_rebuild_jobs", indexes = {
        @Index(name = "idx_mlm_ledger_rebuild_jobs_status_requested_at", columnList = "status, requested_at")
})
public class MlmLedgerRebuildJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private MlmLedgerRebuildJobStatus status;

    @Column(name = "requested_by_admin_id")
    private UUID requestedByAdminId;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "batch_size", nullable = false)
    private Integer batchSize;

    @Column(name = "processed_orders", nullable = false)
    private Long processedOrders;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = MlmLedgerRebuildJobStatus.PENDING;
        }
        if (requestedAt == null) {
            requestedAt = OffsetDateTime.now();
        }
        if (processedOrders == null) {
            processedOrders = 0L;
        }
        if (batchSize == null || batchSize <= 0) {
            batchSize = 200;
        }
    }
}
