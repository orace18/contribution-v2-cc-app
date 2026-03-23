package com.ccapp.payments.operation.domain.entity;

import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.common.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "operations", indexes = {
        @Index(name = "idx_operations_user_id", columnList = "user_id"),
        @Index(name = "idx_operations_actor_user_id", columnList = "actor_user_id"),
        @Index(name = "idx_operations_status", columnList = "status"),
        @Index(name = "idx_operations_psp_reference", columnList = "psp_reference")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_operations_idempotency_key", columnNames = "idempotency_key"),
        @UniqueConstraint(name = "uk_operations_psp_reference", columnNames = "psp_reference")
})
public class Operation extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID accountHolderId;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private OperationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private OperationStatus status;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "psp_reference", length = 128)
    private String pspReference;

    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "description", length = 255)
    private String description;
}
