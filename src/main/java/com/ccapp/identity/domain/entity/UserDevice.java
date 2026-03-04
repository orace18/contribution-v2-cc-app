package com.ccapp.identity.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.identity.domain.enums.Platform;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_devices", indexes = {
        @Index(name = "idx_user_devices_user_id", columnList = "user_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_device_identifier", columnNames = {"user_id", "device_identifier"})
})
public class UserDevice extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_devices_user"))
    private User user;

    @Column(name = "device_identifier", nullable = false, length = 128)
    private String deviceIdentifier;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 16)
    private Platform platform;

    @Column(name = "blocked", nullable = false)
    private boolean blocked;

    @Column(name = "blocked_at")
    private OffsetDateTime blockedAt;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;
}
