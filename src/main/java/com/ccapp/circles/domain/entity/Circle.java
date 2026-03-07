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
public class Circle extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "coach_id", nullable = false)
    private UUID coachId;

    @Column(name = "country_code", nullable = false, length = 3)
    private String countryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CircleStatus status;
}
