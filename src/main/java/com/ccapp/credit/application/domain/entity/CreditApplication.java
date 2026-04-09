package com.ccapp.credit.application.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.credit.application.domain.enums.CreditApplicationProductType;
import com.ccapp.credit.application.domain.enums.CreditApplicationStatus;
import com.ccapp.seedcapital.readiness.domain.enums.SeedCapitalCreditBridgeState;
import com.ccapp.seedcapital.readiness.domain.enums.SeedCapitalEligibilityState;
import com.ccapp.seedcapital.savings.domain.enums.ImfSavingsGateState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "credit_applications")
public class CreditApplication extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_holder_id", nullable = false)
    private UUID accountHolderId;

    @Column(name = "circle_id", nullable = false)
    private UUID circleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 32)
    private CreditApplicationProductType productType;

    @Enumerated(EnumType.STRING)
    @Column(name = "bridge_state", nullable = false, length = 48)
    private SeedCapitalCreditBridgeState bridgeState;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_state", nullable = false, length = 32)
    private CreditApplicationStatus workflowState;

    @Column(name = "requested_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal requestedAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "requested_duration_months")
    private Integer requestedDurationMonths;

    @Column(name = "requested_interest_rate", precision = 5, scale = 2)
    private BigDecimal requestedInterestRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "readiness_state", nullable = false, length = 32)
    private SeedCapitalEligibilityState readinessState;

    @Column(name = "readiness_trust_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal readinessTrustScore;

    @Column(name = "credit_eligibility_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal creditEligibilityScore;

    @Column(name = "max_eligible_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal maxEligibleAmount;

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

    @Column(name = "pending_criteria", columnDefinition = "TEXT")
    private String pendingCriteria;

    @Column(name = "linked_loan_id")
    private UUID linkedLoanId;

    @Column(name = "decision_note", length = 1000)
    private String decisionNote;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;

    @Column(name = "bridge_evaluated_at", nullable = false)
    private OffsetDateTime bridgeEvaluatedAt;

    @PrePersist
    protected void onPersist() {
        if (productType == null) {
            productType = CreditApplicationProductType.SEED_CAPITAL;
        }
        if (workflowState == null) {
            workflowState = CreditApplicationStatus.SUBMITTED;
        }
        if (submittedAt == null) {
            submittedAt = OffsetDateTime.now();
        }
        if (bridgeEvaluatedAt == null) {
            bridgeEvaluatedAt = submittedAt;
        }
        if (readinessTrustScore == null) {
            readinessTrustScore = BigDecimal.ZERO;
        }
        if (creditEligibilityScore == null) {
            creditEligibilityScore = BigDecimal.ZERO;
        }
        if (maxEligibleAmount == null) {
            maxEligibleAmount = BigDecimal.ZERO.setScale(4);
        }
        if (requiredSavingsCyclesCount <= 0) {
            requiredSavingsCyclesCount = 2;
        }
    }
}
