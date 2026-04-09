package com.ccapp.circles.domain.entity;

import com.ccapp.circles.domain.enums.CircleStatus;
import com.ccapp.common.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "circles", indexes = {
        @Index(name = "idx_circles_coach_status", columnList = "coach_id, status")
})
/**
 * Core social circle aggregate.
 * Financial truth and coach assignment live outside of this entity.
 */
public class Circle extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /**
     * Legacy compatibility field kept until explicit coach assignment is introduced.
     * It currently stores the circle owner/manager account holder id, not a canonical coach assignment.
     */
    @Deprecated(since = "2026-04", forRemoval = false)
    @Column(name = "coach_id", nullable = false)
    private UUID coachId;

    @Column(name = "country_code", nullable = false, length = 3)
    private String countryCode;

    @Column(name = "config_snapshot", columnDefinition = "TEXT")
    private String configSnapshot;

    @Column(name = "config_version")
    private Integer configVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CircleStatus status;
}
