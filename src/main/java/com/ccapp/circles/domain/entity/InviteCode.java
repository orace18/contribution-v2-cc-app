package com.ccapp.circles.domain.entity;

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
@Table(name = "invite_codes", indexes = {
        @Index(name = "idx_invite_codes_code", columnList = "code"),
        @Index(name = "idx_invite_codes_expires_at", columnList = "expires_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_invite_codes_code", columnNames = "code")
})
public class InviteCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false, length = 6)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invite_id", nullable = false, foreignKey = @ForeignKey(name = "fk_invite_codes_invite"))
    private CircleInvite invite;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "circle_id", nullable = false, foreignKey = @ForeignKey(name = "fk_invite_codes_circle"))
    private Circle circle;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
