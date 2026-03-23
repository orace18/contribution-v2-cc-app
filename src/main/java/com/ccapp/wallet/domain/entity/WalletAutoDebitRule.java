package com.ccapp.wallet.domain.entity;

import com.ccapp.wallet.domain.enums.WalletAutoDebitRuleType;
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
        name = "wallet_auto_debit_rules",
        indexes = {
                @Index(name = "idx_wallet_auto_debit_rules_wallet_enabled_priority",
                        columnList = "wallet_id, enabled, priority")
        }
)
public class WalletAutoDebitRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 32)
    private WalletAutoDebitRuleType ruleType;

    /** Target ledger account code that receives the auto-debit. */
    @Column(name = "destination_account_code", nullable = false, length = 64)
    private String destinationAccountCode;

    /** Fixed amount or percentage of the incoming credit, depending on {@code isPercent}. */
    @Column(name = "amount_or_percent", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountOrPercent;

    /** When true, {@code amountOrPercent} is treated as a percentage of the credit amount. */
    @Column(name = "is_percent", nullable = false)
    private boolean isPercent;

    /** Lower priority = executed first. */
    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "auto_debit_authorized", nullable = false)
    private boolean autoDebitAuthorized;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (!enabled) {
            enabled = true;
        }
    }
}
