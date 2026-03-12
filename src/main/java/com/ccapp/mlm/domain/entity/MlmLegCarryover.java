package com.ccapp.mlm.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@IdClass(MlmLegCarryoverId.class)
@Table(name = "mlm_leg_carryover", indexes = {
        @Index(name = "idx_mlm_leg_carryover_member_id", columnList = "member_id"),
        @Index(name = "idx_mlm_leg_carryover_week_id", columnList = "week_id"),
        @Index(name = "idx_mlm_leg_carryover_leg_position", columnList = "leg_position")
})
public class MlmLegCarryover {

    @Id
    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Id
    @Column(name = "leg_position", nullable = false)
    private Integer legPosition;

    @Id
    @Column(name = "week_id", nullable = false)
    private Integer weekId;

    @Column(name = "volume", nullable = false, precision = 19, scale = 4)
    private BigDecimal volume;
}
