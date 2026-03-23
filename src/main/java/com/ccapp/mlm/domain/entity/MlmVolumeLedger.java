package com.ccapp.mlm.domain.entity;

import com.ccapp.mlm.domain.enums.MlmVolumeTreeType;
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
@Table(name = "mlm_volume_ledger",
        uniqueConstraints = @UniqueConstraint(name = "uk_mlm_volume_ledger_account_order",
                columnNames = {"account_holder_id", "source_order_id", "tree_type"}),
        indexes = {
                @Index(name = "idx_mlm_volume_ledger_account_holder", columnList = "account_holder_id"),
                @Index(name = "idx_mlm_volume_ledger_week_id", columnList = "week_id"),
                @Index(name = "idx_mlm_volume_ledger_leg_position", columnList = "leg_position")
        })
public class MlmVolumeLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_holder_id", nullable = false)
    private UUID accountHolderId;

    @Column(name = "source_member_id", nullable = false)
    private UUID sourceMemberId;

    @Column(name = "source_order_id", nullable = false)
    private UUID sourceOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tree_type", nullable = false, length = 16)
    @Builder.Default
    private MlmVolumeTreeType treeType = MlmVolumeTreeType.PLACEMENT;

    @Column(name = "generation", nullable = false)
    @Builder.Default
    private Integer generation = 0;

    @Column(name = "leg_position")
    private Integer legPosition;

    @Column(name = "volume_cv", nullable = false, precision = 19, scale = 4)
    private BigDecimal volumeCv;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "week_id", nullable = false)
    private Integer weekId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (treeType == null) {
            treeType = MlmVolumeTreeType.PLACEMENT;
        }
        if (generation == null) {
            generation = 0;
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
