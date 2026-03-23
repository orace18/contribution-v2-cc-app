package com.ccapp.credit.domain.entity;

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
@Table(name = "loan_repayments", indexes = {
        @Index(name = "idx_loan_repayments_loan_id", columnList = "loan_id"),
        @Index(name = "idx_loan_repayments_paid_at", columnList = "paid_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_loan_repayments_transaction_id", columnNames = "transaction_id")
})
public class LoanRepayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false, foreignKey = @ForeignKey(name = "fk_loan_repayments_loan"))
    private Loan loan;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "payment_method", nullable = false, length = 32)
    private String paymentMethod;

    @Column(name = "institution_id")
    private UUID institutionId;

    @Column(name = "transaction_id", length = 128)
    private String transactionId;

    @Column(name = "paid_at", nullable = false)
    private OffsetDateTime paidAt;

    @PrePersist
    protected void onCreate() {
        if (paidAt == null) {
            paidAt = OffsetDateTime.now();
        }
    }
}
