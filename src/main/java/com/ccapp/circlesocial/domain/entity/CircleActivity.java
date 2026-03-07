package com.ccapp.circlesocial.domain.entity;

import com.ccapp.circlesocial.domain.enums.ActivityType;
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
@Table(name = "circle_activity", indexes = {
        @Index(name = "idx_circle_activity_circle_created_at", columnList = "circle_id, created_at")
})
public class CircleActivity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "circle_id", nullable = false)
    private UUID circleId;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 32)
    private ActivityType activityType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "message", nullable = false, length = 255)
    private String message;
}
