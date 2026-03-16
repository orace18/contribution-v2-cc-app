package com.ccapp.circles.domain.entity;

import com.ccapp.circles.domain.enums.CircleCycleStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "circle_cycles", indexes = {
        @Index(name = "idx_circle_cycles_circle_status", columnList = "circle_id, status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_circle_cycles_circle_cycle_number", columnNames = {"circle_id", "cycle_number"})
})
public class CircleCycle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "circle_id", nullable = false, foreignKey = @ForeignKey(name = "fk_circle_cycles_circle"))
    private Circle circle;

    @Column(name = "cycle_number", nullable = false)
    private Integer cycleNumber;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CircleCycleStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (status == null) {
            status = CircleCycleStatus.FORMING;
        }
    }
}
