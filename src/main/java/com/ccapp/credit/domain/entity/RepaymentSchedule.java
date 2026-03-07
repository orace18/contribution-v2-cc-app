package com.ccapp.credit.domain.entity;

import com.ccapp.credit.domain.enums.InstallmentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "repayment_schedules", indexes = {
        @Index(name = "idx_repayment_schedules_loan_id", columnList = "loan_id"),
        @Index(name = "idx_repayment_schedules_due_date", columnList = "due_date")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_repayment_schedules_loan_installment", columnNames = {"loan_id", "installment_number"})
})
public class RepaymentSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false, foreignKey = @ForeignKey(name = "fk_repayment_schedules_loan"))
    private Loan loan;

    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;

    @Column(name = "amount_due", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountDue;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private InstallmentStatus status;

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = InstallmentStatus.PENDING;
        }
    }
}
