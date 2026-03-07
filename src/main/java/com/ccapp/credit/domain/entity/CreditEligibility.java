package com.ccapp.credit.domain.entity;

import jakarta.persistence.*;
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
@Table(name = "credit_eligibility")
public class CreditEligibility {

    @Id
    @Column(name = "account_holder_id", nullable = false)
    private UUID accountHolderId;

    @Column(name = "circle_id")
    private UUID circleId;

    @Column(name = "rms_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal rmsScore;

    @Column(name = "circle_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal circleScore;

    @Column(name = "eligibility_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal eligibilityScore;

    @Column(name = "max_eligible_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal maxEligibleAmount;

    @Column(name = "evaluated_at", nullable = false)
    private OffsetDateTime evaluatedAt;

    @PrePersist
    protected void onCreate() {
        if (evaluatedAt == null) {
            evaluatedAt = OffsetDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        evaluatedAt = OffsetDateTime.now();
    }
}
