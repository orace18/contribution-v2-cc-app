package com.ccapp.circles.domain.entity;

import com.ccapp.circles.domain.enums.CircleMemberRole;
import com.ccapp.common.model.AuditableEntity;
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
@Table(name = "circle_members", indexes = {
        @Index(name = "idx_circle_members_account_holder_id", columnList = "account_holder_id"),
        @Index(name = "idx_circle_members_circle_id", columnList = "circle_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_circle_members_circle_account_holder", columnNames = {"circle_id", "account_holder_id"})
})
/**
 * Social membership inside a circle.
 * Legacy financial columns are kept only for backward compatibility until PR4 removes the old circle finance path.
 */
public class CircleMember extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "circle_id", nullable = false, foreignKey = @ForeignKey(name = "fk_circle_members_circle"))
    private Circle circle;

    @Column(name = "account_holder_id", nullable = false)
    private UUID accountHolderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private CircleMemberRole role;

    @Deprecated(since = "2026-04", forRemoval = false)
    @Column(name = "is_financial_member", nullable = false)
    private boolean financialMember;

    @Column(name = "business_activity", length = 255)
    private String businessActivity;

    @Deprecated(since = "2026-04", forRemoval = false)
    @Column(name = "loan_status", length = 32)
    private String loanStatus;

    @Deprecated(since = "2026-04", forRemoval = false)
    @Column(name = "repayment_progress", precision = 5, scale = 2)
    private BigDecimal repaymentProgress;

    @Column(name = "joined_at", nullable = false)
    private OffsetDateTime joinedAt;
}
