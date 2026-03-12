package com.ccapp.mlm.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@IdClass(MlmWeeklySnapshotId.class)
@Table(name = "mlm_weekly_snapshots", indexes = {
        @Index(name = "idx_mlm_weekly_snapshots_week", columnList = "week_id"),
        @Index(name = "idx_mlm_weekly_snapshots_account_holder", columnList = "account_holder_id")
})
public class MlmWeeklySnapshot {

    @Id
    @Column(name = "week_id", nullable = false)
    private Integer weekId;

    @Id
    @Column(name = "account_holder_id", nullable = false)
    private UUID accountHolderId;

    @Column(name = "team_volume", nullable = false, precision = 19, scale = 4)
    private BigDecimal teamVolume;

    @Column(name = "team_members", nullable = false)
    private Long teamMembers;

    @Column(name = "commissions_generated", nullable = false, precision = 19, scale = 4)
    private BigDecimal commissionsGenerated;

    @Column(name = "commissions_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal commissionsPaid;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (teamVolume == null) {
            teamVolume = BigDecimal.ZERO;
        }
        if (teamMembers == null) {
            teamMembers = 0L;
        }
        if (commissionsGenerated == null) {
            commissionsGenerated = BigDecimal.ZERO;
        }
        if (commissionsPaid == null) {
            commissionsPaid = BigDecimal.ZERO;
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
