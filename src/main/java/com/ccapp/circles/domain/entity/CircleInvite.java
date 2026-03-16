package com.ccapp.circles.domain.entity;

import com.ccapp.circles.domain.enums.CircleInviteStatus;
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
@Table(name = "circle_invites", indexes = {
        @Index(name = "idx_circle_invites_circle_status", columnList = "circle_id, status"),
        @Index(name = "idx_circle_invites_expires_at", columnList = "expires_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_circle_invites_token", columnNames = "invitation_token")
})
public class CircleInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "circle_id", nullable = false, foreignKey = @ForeignKey(name = "fk_circle_invites_circle"))
    private Circle circle;

    @Column(name = "created_by_account_holder_id", nullable = false)
    private UUID createdByAccountHolderId;

    @Column(name = "invitation_token", nullable = false, length = 255)
    private String invitationToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CircleInviteStatus status;

    @Column(name = "max_uses", nullable = false)
    private int maxUses;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (status == null) {
            status = CircleInviteStatus.ACTIVE;
        }
    }
}
