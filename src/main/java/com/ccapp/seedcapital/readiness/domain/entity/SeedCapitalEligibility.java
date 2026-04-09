package com.ccapp.seedcapital.readiness.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.seedcapital.readiness.domain.enums.SeedCapitalEligibilityState;
import com.ccapp.seedcapital.savings.domain.enums.ImfSavingsGateState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
@Table(name = "seed_capital_eligibility")
public class SeedCapitalEligibility extends AuditableEntity {

    @Id
    @Column(name = "circle_id", nullable = false)
    private UUID circleId;

    @Column(name = "eligible", nullable = false)
    private boolean eligible;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 32)
    private SeedCapitalEligibilityState state;

    @Column(name = "trust_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal trustScore;

    @Column(name = "valid_circle_size", nullable = false)
    private boolean validCircleSize;

    @Column(name = "membership_compliant", nullable = false)
    private boolean membershipCompliant;

    @Column(name = "meetings_requirement_met", nullable = false)
    private boolean meetingsRequirementMet;

    @Column(name = "coach_assigned", nullable = false)
    private boolean coachAssigned;

    @Column(name = "trust_score_threshold_met", nullable = false)
    private boolean trustScoreThresholdMet;

    @Column(name = "imf_channel_coverage_met", nullable = false)
    private boolean imfChannelCoverageMet;

    @Column(name = "savings_discipline_met", nullable = false)
    private boolean savingsDisciplineMet;

    @Enumerated(EnumType.STRING)
    @Column(name = "savings_gate_state", length = 32)
    private ImfSavingsGateState savingsGateState;

    @Column(name = "configured_imf_channels_count", nullable = false)
    private int configuredImfChannelsCount;

    @Column(name = "ready_imf_channels_count", nullable = false)
    private int readyImfChannelsCount;

    @Column(name = "qualifying_savings_cycles_count", nullable = false)
    private int qualifyingSavingsCyclesCount;

    @Column(name = "required_savings_cycles_count", nullable = false)
    private int requiredSavingsCyclesCount;

    @Column(name = "blocking_reasons", columnDefinition = "TEXT")
    private String blockingReasons;

    @Column(name = "satisfied_criteria", columnDefinition = "TEXT")
    private String satisfiedCriteria;

    @Column(name = "pending_criteria", columnDefinition = "TEXT")
    private String pendingCriteria;

    @Column(name = "evaluated_at", nullable = false)
    private OffsetDateTime evaluatedAt;

    @PrePersist
    protected void onPersist() {
        initializeDefaults();
    }

    @PreUpdate
    protected void onPersistOrUpdate() {
        initializeDefaults();
    }

    private void initializeDefaults() {
        if (state == null) {
            state = SeedCapitalEligibilityState.BLOCKED;
        }
        if (trustScore == null) {
            trustScore = BigDecimal.ZERO;
        }
        if (requiredSavingsCyclesCount <= 0) {
            requiredSavingsCyclesCount = 2;
        }
        if (evaluatedAt == null) {
            evaluatedAt = OffsetDateTime.now();
        }
    }
}
