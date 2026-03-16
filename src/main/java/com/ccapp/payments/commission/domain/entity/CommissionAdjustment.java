package com.ccapp.payments.commission.domain.entity;

import com.ccapp.payments.commission.domain.enums.CommissionAdjustmentType;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "commission_adjustments",
        uniqueConstraints = @UniqueConstraint(name = "uk_commission_adjustments_order_account_type",
                columnNames = {"reference_order_id", "account_holder_id", "adjustment_type"}),
        indexes = {
                @Index(name = "idx_commission_adjustments_reference_order", columnList = "reference_order_id"),
                @Index(name = "idx_commission_adjustments_account_holder", columnList = "account_holder_id")
        })
public class CommissionAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reference_order_id", nullable = false)
    private UUID referenceOrderId;

    @Column(name = "account_holder_id")
    private UUID accountHolderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false, length = 64)
    private CommissionAdjustmentType adjustmentType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
