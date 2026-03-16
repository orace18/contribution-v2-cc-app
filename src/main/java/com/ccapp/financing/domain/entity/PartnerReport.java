package com.ccapp.financing.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "partner_reports", indexes = {
        @Index(name = "idx_partner_reports_partner_created", columnList = "partner_id, created_at")
})
public class PartnerReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "partner_id", nullable = false)
    private UUID partnerId;

    @Column(name = "report_type", nullable = false, length = 32)
    private String reportType;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "loan_volume", nullable = false, precision = 19, scale = 4)
    private BigDecimal loanVolume;

    @Column(name = "repayment_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal repaymentRate;

    @Column(name = "defaults", nullable = false)
    private long defaults;

    @Column(name = "portfolio_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal portfolioValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
