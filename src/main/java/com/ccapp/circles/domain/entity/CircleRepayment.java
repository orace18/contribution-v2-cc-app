package com.ccapp.circles.domain.entity;

import com.ccapp.circles.domain.enums.CircleRepaymentStatus;
import jakarta.persistence.*;
import lombok.*;

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
@Table(name = "circle_repayments", indexes = {
        @Index(name = "idx_circle_repayments_loan_status", columnList = "loan_id, status")
})
public class CircleRepayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false, foreignKey = @ForeignKey(name = "fk_circle_repayments_loan"))
    private CircleLoan loan;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CircleRepaymentStatus status;

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = CircleRepaymentStatus.PENDING;
        }
    }
}
