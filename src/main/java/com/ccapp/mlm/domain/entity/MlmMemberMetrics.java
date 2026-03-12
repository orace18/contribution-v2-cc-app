package com.ccapp.mlm.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
@Table(name = "mlm_member_metrics", indexes = {
        @Index(name = "idx_mlm_member_metrics_last_updated", columnList = "last_updated")
})
public class MlmMemberMetrics {

    @Id
    @Column(name = "account_holder_id", nullable = false)
    private UUID accountHolderId;

    @Column(name = "team_size", nullable = false)
    private Long teamSize;

    @Column(name = "active_team_members", nullable = false)
    private Long activeTeamMembers;

    @Column(name = "total_volume", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalVolume;

    @Column(name = "total_commissions", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalCommissions;

    @Column(name = "last_updated", nullable = false)
    private OffsetDateTime lastUpdated;

    @PrePersist
    @PreUpdate
    protected void onUpsert() {
        if (teamSize == null) {
            teamSize = 0L;
        }
        if (activeTeamMembers == null) {
            activeTeamMembers = 0L;
        }
        if (totalVolume == null) {
            totalVolume = BigDecimal.ZERO;
        }
        if (totalCommissions == null) {
            totalCommissions = BigDecimal.ZERO;
        }
        lastUpdated = OffsetDateTime.now();
    }
}
