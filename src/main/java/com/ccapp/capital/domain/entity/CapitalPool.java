package com.ccapp.capital.domain.entity;

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
@Table(name = "capital_pools", indexes = {
        @Index(name = "idx_capital_pools_fund_id", columnList = "fund_id"),
        @Index(name = "idx_capital_pools_circle_id", columnList = "circle_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_capital_pools_fund_circle", columnNames = {"fund_id", "circle_id"})
})
public class CapitalPool {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fund_id", nullable = false, foreignKey = @ForeignKey(name = "fk_capital_pools_fund"))
    private Fund fund;

    @Column(name = "circle_id", nullable = false)
    private UUID circleId;

    @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal allocatedAmount;

    @Column(name = "used_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal usedAmount;

    @PrePersist
    protected void onCreate() {
        if (usedAmount == null) {
            usedAmount = BigDecimal.ZERO;
        }
    }
}
