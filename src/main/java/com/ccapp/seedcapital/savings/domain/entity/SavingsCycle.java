package com.ccapp.seedcapital.savings.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.seedcapital.savings.domain.enums.SavingsCycleStatus;
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
@Table(name = "savings_cycles", indexes = {
        @Index(name = "idx_savings_cycles_circle_status_window", columnList = "circle_id, status, window_start DESC")
})
public class SavingsCycle extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "circle_id", nullable = false)
    private UUID circleId;

    @Column(name = "created_by_account_holder_id", nullable = false)
    private UUID createdByAccountHolderId;

    @Column(name = "cycle_number")
    private Integer cycleNumber;

    @Column(name = "window_start", nullable = false)
    private OffsetDateTime windowStart;

    @Column(name = "window_end", nullable = false)
    private OffsetDateTime windowEnd;

    @Column(name = "expected_amount_per_member", nullable = false, precision = 19, scale = 4)
    private BigDecimal expectedAmountPerMember;

    @Column(name = "country_code", nullable = false, length = 3)
    private String countryCode;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "participant_count", nullable = false)
    private Integer participantCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SavingsCycleStatus status;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @PrePersist
    protected void onPersist() {
        if (status == null) {
            status = SavingsCycleStatus.OPEN;
        }
        if (participantCount == null) {
            participantCount = 0;
        }
    }
}
