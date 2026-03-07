package com.ccapp.credit.domain.entity;

import com.ccapp.credit.domain.enums.LoanStatus;
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
@Table(name = "loans", indexes = {
        @Index(name = "idx_loans_account_holder_status", columnList = "account_holder_id, status"),
        @Index(name = "idx_loans_circle_id", columnList = "circle_id")
})
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_holder_id", nullable = false)
    private UUID accountHolderId;

    @Column(name = "circle_id", nullable = false)
    private UUID circleId;

    @Column(name = "country_code", nullable = false, length = 3)
    private String countryCode;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(name = "duration_months", nullable = false)
    private Integer durationMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private LoanStatus status;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "disbursed_at")
    private OffsetDateTime disbursedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (status == null) {
            status = LoanStatus.PENDING;
        }
    }
}
