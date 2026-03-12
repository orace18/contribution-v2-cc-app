package com.ccapp.mlm.domain.entity;

import com.ccapp.mlm.domain.enums.WeeklyBonusJobRunStatus;
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
@Table(name = "mlm_weekly_bonus_job_runs",
        uniqueConstraints = @UniqueConstraint(name = "uk_mlm_weekly_bonus_job_runs_week", columnNames = "week_id"),
        indexes = {
                @Index(name = "idx_mlm_weekly_bonus_job_runs_week", columnList = "week_id"),
                @Index(name = "idx_mlm_weekly_bonus_job_runs_finished_at", columnList = "finished_at")
        })
public class MlmWeeklyBonusJobRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "week_id", nullable = false)
    private Integer weekId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private WeeklyBonusJobRunStatus status;

    @Column(name = "members_processed", nullable = false)
    private Integer membersProcessed;

    @Column(name = "commissions_created", nullable = false)
    private Integer commissionsCreated;

    @Column(name = "carryovers_stored", nullable = false)
    private Integer carryoversStored;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "finished_at", nullable = false)
    private OffsetDateTime finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (membersProcessed == null) {
            membersProcessed = 0;
        }
        if (commissionsCreated == null) {
            commissionsCreated = 0;
        }
        if (carryoversStored == null) {
            carryoversStored = 0;
        }
        if (startedAt == null) {
            startedAt = OffsetDateTime.now();
        }
        if (finishedAt == null) {
            finishedAt = startedAt;
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
