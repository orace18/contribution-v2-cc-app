package com.ccapp.seedcapital.readiness.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "circle_trust_scores")
public class CircleTrustScore extends AuditableEntity {

    @Id
    @Column(name = "circle_id", nullable = false)
    private UUID circleId;

    @Column(name = "members_count", nullable = false)
    private Integer membersCount;

    @Column(name = "financially_ready_members_count", nullable = false)
    private Integer financiallyReadyMembersCount;

    @Column(name = "completed_meetings_count", nullable = false)
    private Integer completedMeetingsCount;

    @Column(name = "recent_activity_count", nullable = false)
    private Integer recentActivityCount;

    @Column(name = "coach_assigned", nullable = false)
    private boolean coachAssigned;

    @Column(name = "membership_compliance_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal membershipComplianceScore;

    @Column(name = "circle_size_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal circleSizeScore;

    @Column(name = "meetings_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal meetingsScore;

    @Column(name = "activity_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal activityScore;

    @Column(name = "coach_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal coachScore;

    @Column(name = "global_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal globalScore;

    @PrePersist
    protected void onPersist() {
        if (membersCount == null) {
            membersCount = 0;
        }
        if (financiallyReadyMembersCount == null) {
            financiallyReadyMembersCount = 0;
        }
        if (completedMeetingsCount == null) {
            completedMeetingsCount = 0;
        }
        if (recentActivityCount == null) {
            recentActivityCount = 0;
        }
        if (membershipComplianceScore == null) {
            membershipComplianceScore = BigDecimal.ZERO;
        }
        if (circleSizeScore == null) {
            circleSizeScore = BigDecimal.ZERO;
        }
        if (meetingsScore == null) {
            meetingsScore = BigDecimal.ZERO;
        }
        if (activityScore == null) {
            activityScore = BigDecimal.ZERO;
        }
        if (coachScore == null) {
            coachScore = BigDecimal.ZERO;
        }
        if (globalScore == null) {
            globalScore = BigDecimal.ZERO;
        }
    }
}
