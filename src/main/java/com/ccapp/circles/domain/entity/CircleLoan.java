package com.ccapp.circles.domain.entity;

import com.ccapp.circles.domain.enums.CircleLoanStatus;
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
@Table(name = "circle_loans", indexes = {
        @Index(name = "idx_circle_loans_cycle_status", columnList = "cycle_id, status"),
        @Index(name = "idx_circle_loans_borrower_status", columnList = "borrower_id, status")
})
public class CircleLoan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cycle_id", nullable = false, foreignKey = @ForeignKey(name = "fk_circle_loans_cycle"))
    private CircleCycle cycle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "borrower_id", nullable = false, foreignKey = @ForeignKey(name = "fk_circle_loans_borrower"))
    private CircleMember borrower;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "interest", nullable = false, precision = 5, scale = 2)
    private BigDecimal interest;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CircleLoanStatus status;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "disbursed_at")
    private OffsetDateTime disbursedAt;

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = CircleLoanStatus.REQUESTED;
        }
    }
}
