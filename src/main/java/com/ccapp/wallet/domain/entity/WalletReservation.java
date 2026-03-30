package com.ccapp.wallet.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.wallet.domain.enums.WalletReservationStatus;
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
@Table(
        name = "wallet_reservations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_wallet_reservations_operation_id", columnNames = "operation_id")
        },
        indexes = {
                @Index(name = "idx_wallet_reservations_wallet_id",     columnList = "wallet_id"),
                @Index(name = "idx_wallet_reservations_wallet_status", columnList = "wallet_id, status"),
                @Index(name = "idx_wallet_reservations_operation_id", columnList = "operation_id")
        }
)
public class WalletReservation extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "operation_id")
    private UUID operationId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    @Column(name = "expiry_date")
    private OffsetDateTime expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private WalletReservationStatus status;

    @PrePersist
    protected void onPrePersist() {
        if (status == null) {
            status = WalletReservationStatus.ACTIVE;
        }
    }
}
