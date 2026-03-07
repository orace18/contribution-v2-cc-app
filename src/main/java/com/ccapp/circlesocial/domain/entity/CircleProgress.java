package com.ccapp.circlesocial.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "circle_progress")
public class CircleProgress {

    @Id
    @Column(name = "circle_id", nullable = false)
    private UUID circleId;

    @Column(name = "members_count", nullable = false)
    private Integer membersCount;

    @Column(name = "members_completed_training", nullable = false)
    private Integer membersCompletedTraining;

    @Column(name = "members_submitted_project", nullable = false)
    private Integer membersSubmittedProject;

    @Column(name = "members_paid_contribution", nullable = false)
    private Integer membersPaidContribution;

    @Column(name = "progress_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal progressPercentage;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (membersCount == null) {
            membersCount = 0;
        }
        if (membersCompletedTraining == null) {
            membersCompletedTraining = 0;
        }
        if (membersSubmittedProject == null) {
            membersSubmittedProject = 0;
        }
        if (membersPaidContribution == null) {
            membersPaidContribution = 0;
        }
        if (progressPercentage == null) {
            progressPercentage = BigDecimal.ZERO;
        }
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
