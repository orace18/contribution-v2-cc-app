package com.ccapp.circles.domain.entity;

import com.ccapp.circles.domain.enums.CircleContributionStatus;
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
@Table(name = "circle_contributions", indexes = {
        @Index(name = "idx_circle_contributions_cycle_status", columnList = "cycle_id, status"),
        @Index(name = "idx_circle_contributions_member_status", columnList = "member_id, status")
})
@Deprecated(since = "2026-04", forRemoval = false)
public class CircleContribution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cycle_id", nullable = false, foreignKey = @ForeignKey(name = "fk_circle_contributions_cycle"))
    private CircleCycle cycle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_circle_contributions_member"))
    private CircleMember member;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CircleContributionStatus status;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = CircleContributionStatus.PENDING;
        }
    }
}
