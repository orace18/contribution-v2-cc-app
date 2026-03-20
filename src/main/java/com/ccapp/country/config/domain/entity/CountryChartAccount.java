package com.ccapp.country.config.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.country.config.domain.enums.CountryAccountGroup;
import com.ccapp.country.config.domain.enums.CountryChartAccountStatus;
import com.ccapp.country.config.domain.enums.CountryChartAccountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "country_chart_accounts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_country_chart_accounts_country_code_account_code",
                        columnNames = {"country_code", "account_code"})
        },
        indexes = {
                @Index(name = "idx_country_chart_accounts_country_status", columnList = "country_code, status"),
                @Index(name = "idx_country_chart_accounts_country_code", columnList = "country_code"),
                @Index(name = "idx_country_chart_accounts_holder_id", columnList = "account_holder_id")
        })
public class CountryChartAccount extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "country_code", nullable = false, length = 3)
    private String countryCode;

    @Column(name = "account_code", nullable = false, length = 64)
    private String accountCode;

    @Column(name = "account_number", nullable = false, length = 64)
    private String accountNumber;

    @Column(name = "account_name", nullable = false, length = 180)
    private String accountName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_group", nullable = false, length = 64)
    private CountryAccountGroup accountGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 32)
    private CountryChartAccountType accountType;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "opening_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal openingBalance;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CountryChartAccountStatus status;

    /**
     * Links this account to a specific AccountHolder.
     * Non-null only for USER_WALLET group accounts — one wallet per holder per country.
     * Shared group accounts (commission pool, etc.) leave this null.
     */
    @Column(name = "account_holder_id", nullable = true)
    private UUID accountHolderId;

    @PrePersist
    protected void onCreate() {
        super.onCreate();
        normalizeFields();
        if (countryCode != null) {
            countryCode = countryCode.trim().toUpperCase(Locale.ROOT);
        }
        if (openingBalance == null) {
            openingBalance = BigDecimal.ZERO;
        }
        if ((accountNumber == null || accountNumber.isBlank()) && accountCode != null && !accountCode.isBlank()) {
            accountNumber = accountCode;
        }
        if ((accountCode == null || accountCode.isBlank()) && accountNumber != null && !accountNumber.isBlank()) {
            accountCode = accountNumber;
        }
        if (status == null) {
            status = CountryChartAccountStatus.ACTIVE;
        }
        if (accountType == null) {
            accountType = CountryChartAccountType.ASSET;
        }
        if (accountGroup == null) {
            accountGroup = CountryAccountGroup.PLATFORM_REVENUE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        super.onUpdate();
        normalizeFields();
    }

    private void normalizeFields() {
        if (countryCode != null) {
            countryCode = countryCode.trim().toUpperCase(Locale.ROOT);
        }
        if (accountCode != null) {
            accountCode = accountCode.trim().toUpperCase(Locale.ROOT);
        }
        if (accountNumber != null) {
            accountNumber = accountNumber.trim().toUpperCase(Locale.ROOT);
        }
        if ((accountNumber == null || accountNumber.isBlank()) && accountCode != null && !accountCode.isBlank()) {
            accountNumber = accountCode.trim().toUpperCase(Locale.ROOT);
        }
        if ((accountCode == null || accountCode.isBlank()) && accountNumber != null && !accountNumber.isBlank()) {
            accountCode = accountNumber.trim().toUpperCase(Locale.ROOT);
        }
        if (currency != null) {
            currency = currency.trim().toUpperCase(Locale.ROOT);
        }
    }
}
