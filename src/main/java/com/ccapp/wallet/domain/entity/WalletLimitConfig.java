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
        name = "wallet_limit_configs",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_wallet_limit_configs_country_kyc_currency",
                        columnNames = {"country_code", "kyc_level", "currency"}
                )
        },
        indexes = {
                @Index(name = "idx_wallet_limit_configs_country_kyc", columnList = "country_code, kyc_level")
        }
)
public class WalletLimitConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "country_code", nullable = false, length = 3)
    private String countryCode;

    /** Must match a KycStatus enum name (NOT_STARTED, PENDING, VERIFIED, REJECTED). */
    @Column(name = "kyc_level", nullable = false, length = 16)
    private String kycLevel;

    @Column(name = "max_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal maxBalance;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
