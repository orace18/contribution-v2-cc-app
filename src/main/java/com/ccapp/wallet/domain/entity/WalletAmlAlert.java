package com.ccapp.wallet.domain.entity;

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
        name = "wallet_aml_alerts",
        indexes = {
                @Index(name = "idx_wallet_aml_alerts_wallet_id",      columnList = "wallet_id"),
                @Index(name = "idx_wallet_aml_alerts_account_holder", columnList = "account_holder_id")
        }
)
public class WalletAmlAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "account_holder_id", nullable = false)
    private UUID accountHolderId;

    /** MAX_BALANCE_EXCEEDED | SUSPICIOUS_PATTERN */
    @Column(name = "alert_type", nullable = false, length = 32)
    private String alertType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "resolved", nullable = false)
    private boolean resolved;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
