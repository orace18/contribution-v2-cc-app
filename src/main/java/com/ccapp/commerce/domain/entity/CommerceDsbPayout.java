package com.ccapp.commerce.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.commerce.domain.enums.CommerceDsbBeneficiaryRole;
import com.ccapp.commerce.domain.enums.CommerceDsbPayoutStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "commerce_dsb_payouts",
        uniqueConstraints = @UniqueConstraint(name = "uk_commerce_dsb_payouts_order_role", columnNames = {"order_id", "role"}),
        indexes = {
                @Index(name = "idx_commerce_dsb_payouts_order_created", columnList = "order_id, created_at"),
                @Index(name = "idx_commerce_dsb_payouts_beneficiary_created", columnList = "beneficiary_account_holder_id, created_at")
        })
public class CommerceDsbPayout extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "beneficiary_account_holder_id", nullable = false)
    private UUID beneficiaryAccountHolderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private CommerceDsbBeneficiaryRole role;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "operation_id")
    private UUID operationId;

    @Column(name = "reversal_operation_id")
    private UUID reversalOperationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CommerceDsbPayoutStatus status;
}
