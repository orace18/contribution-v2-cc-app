package com.ccapp.payments.commission.domain.entity;

import com.ccapp.payments.commission.domain.enums.CommissionType;
import com.ccapp.payments.commission.domain.enums.CommissionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
@Table(name = "mlm_commissions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_mlm_commissions_operation_member_generation",
                        columnNames = {"operation_id", "beneficiary_account_holder_id", "generation"})
        },
        indexes = {
                @Index(name = "idx_mlm_commissions_beneficiary_created",
                        columnList = "beneficiary_account_holder_id, created_at"),
                @Index(name = "idx_mlm_commissions_type_week", columnList = "commission_type, week_id"),
                @Index(name = "idx_mlm_commissions_week_type_status", columnList = "week_id, commission_type, status"),
                @Index(name = "idx_mlm_commissions_operation_generation", columnList = "operation_id, generation"),
                @Index(name = "idx_mlm_commissions_payout_batch", columnList = "payout_batch_id"),
                @Index(name = "idx_mlm_commissions_payout_item", columnList = "payout_item_id")
        })
public class MlmCommission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "operation_id")
    private UUID operationId;

    @Column(name = "beneficiary_account_holder_id", nullable = false)
    private UUID beneficiaryAccountHolderId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "source", nullable = false, length = 64)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(name = "commission_type", nullable = false, length = 32)
    private CommissionType commissionType;

    @Column(name = "generation")
    private Integer generation;

    @Column(name = "week_id")
    private Integer weekId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CommissionStatus status;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "payout_batch_id")
    private UUID payoutBatchId;

    @Column(name = "payout_item_id")
    private UUID payoutItemId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (commissionType == null) {
            commissionType = CommissionType.SUBSCRIPTION;
        }
        if (status == null) {
            status = CommissionStatus.PENDING;
        }
    }
}
