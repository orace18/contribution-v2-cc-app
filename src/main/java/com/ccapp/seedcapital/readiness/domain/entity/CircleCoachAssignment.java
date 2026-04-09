package com.ccapp.seedcapital.readiness.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.seedcapital.readiness.domain.enums.CircleCoachAssignmentSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
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
@Table(name = "circle_coach_assignments")
public class CircleCoachAssignment extends AuditableEntity {

    @Id
    @Column(name = "circle_id", nullable = false)
    private UUID circleId;

    @Column(name = "coach_account_holder_id", nullable = false)
    private UUID coachAccountHolderId;

    @Column(name = "assigned_by_account_holder_id")
    private UUID assignedByAccountHolderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    private CircleCoachAssignmentSource source;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt;

    @PrePersist
    protected void onPersist() {
        if (source == null) {
            source = CircleCoachAssignmentSource.MANUAL;
        }
        if (assignedAt == null) {
            assignedAt = OffsetDateTime.now();
        }
    }
}
