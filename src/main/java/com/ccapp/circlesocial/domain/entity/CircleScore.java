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
@Table(name = "circle_scores")
public class CircleScore {

    @Id
    @Column(name = "circle_id", nullable = false)
    private UUID circleId;

    @Column(name = "activity_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal activityScore;

    @Column(name = "training_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal trainingScore;

    @Column(name = "repayment_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal repaymentScore;

    @Column(name = "global_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal globalScore;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (activityScore == null) {
            activityScore = BigDecimal.ZERO;
        }
        if (trainingScore == null) {
            trainingScore = BigDecimal.ZERO;
        }
        if (repaymentScore == null) {
            repaymentScore = BigDecimal.ZERO;
        }
        if (globalScore == null) {
            globalScore = BigDecimal.ZERO;
        }
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
