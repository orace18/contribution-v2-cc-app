package com.ccapp.seedcapital.savings.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.seedcapital.savings.domain.enums.MemberSavingsContributionStatus;
import com.ccapp.seedcapital.savings.domain.enums.SavingsContributionConfirmationSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "member_savings_contributions", indexes = {
        @Index(name = "idx_member_savings_contributions_cycle_status", columnList = "cycle_id, status"),
        @Index(name = "idx_member_savings_contributions_member_status", columnList = "account_holder_id, status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_member_savings_contributions_cycle_member", columnNames = {"cycle_id", "account_holder_id"})
})
public class MemberSavingsContribution extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cycle_id", nullable = false)
    private UUID cycleId;

    @Column(name = "account_holder_id", nullable = false)
    private UUID accountHolderId;

    @Column(name = "expected_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal expectedAmount;

    @Column(name = "confirmed_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal confirmedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private MemberSavingsContributionStatus status;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "partner_reference", length = 128)
    private String partnerReference;

    @Column(name = "transaction_reference", length = 128)
    private String transactionReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "confirmation_source", length = 32)
    private SavingsContributionConfirmationSource confirmationSource;

    @Column(name = "confirmation_note", length = 500)
    private String confirmationNote;

    @PrePersist
    protected void onPersist() {
        if (confirmedAmount == null) {
            confirmedAmount = BigDecimal.ZERO.setScale(4);
        }
        if (status == null) {
            status = MemberSavingsContributionStatus.EXPECTED;
        }
    }
}
