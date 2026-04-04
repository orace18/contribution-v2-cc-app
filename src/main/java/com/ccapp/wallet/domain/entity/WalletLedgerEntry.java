package com.ccapp.wallet.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.wallet.domain.enums.WalletLedgerCategory;
import com.ccapp.wallet.domain.enums.WalletLedgerEntryType;
import com.ccapp.wallet.domain.enums.WalletLedgerStatus;
import jakarta.persistence.*;
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
@Table(name = "wallet_ledger", indexes = {
        @Index(name = "idx_wallet_ledger_wallet_created", columnList = "wallet_id, created_at"),
        @Index(name = "idx_wallet_ledger_wallet_status_created", columnList = "wallet_id, status, created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_wallet_ledger_wallet_operation", columnNames = {"wallet_id", "operation_id"})
})
public class WalletLedgerEntry extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "operation_id", nullable = false)
    private UUID operationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 16)
    private WalletLedgerEntryType entryType;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private WalletLedgerCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private WalletLedgerStatus status;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
}
