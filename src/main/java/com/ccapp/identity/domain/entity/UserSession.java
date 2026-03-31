package com.ccapp.identity.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.identity.domain.enums.SecurityRiskLevel;
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
@Table(name = "user_sessions", indexes = {
        @Index(name = "idx_user_sessions_user_id", columnList = "user_id"),
        @Index(name = "idx_user_sessions_device_id", columnList = "device_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_sessions_refresh_hash", columnNames = "refresh_token_hash")
})
public class UserSession extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_sessions_user"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_sessions_device"))
    private UserDevice device;

    @Column(name = "refresh_token_hash", nullable = false, length = 128)
    private String refreshTokenHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 16)
    private SecurityRiskLevel riskLevel;

    @Column(name = "country_code", length = 8)
    private String countryCode;
}
