package com.ccapp.capital.domain.entity;

import com.ccapp.credit.domain.entity.Loan;
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
@Table(name = "loan_funding", indexes = {
        @Index(name = "idx_loan_funding_fund_id", columnList = "fund_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_loan_funding_loan_id", columnNames = "loan_id")
})
public class LoanFunding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false, foreignKey = @ForeignKey(name = "fk_loan_funding_loan"))
    private Loan loan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fund_id", nullable = false, foreignKey = @ForeignKey(name = "fk_loan_funding_fund"))
    private Fund fund;

    @Column(name = "funded_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal fundedAmount;

    @Column(name = "funded_at", nullable = false)
    private OffsetDateTime fundedAt;

    @PrePersist
    protected void onCreate() {
        if (fundedAt == null) {
            fundedAt = OffsetDateTime.now();
        }
    }
}
