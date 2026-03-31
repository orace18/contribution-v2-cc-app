package com.ccapp.identity.domain.entity;

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
@Table(name = "auth_token_usage", indexes = {
        @Index(name = "idx_auth_token_usage_hash_used_at", columnList = "token_hash, used_at"),
        @Index(name = "idx_auth_token_usage_user_id", columnList = "user_id")
})
public class AuthTokenUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "token_hash", nullable = false, length = 128)
    private String tokenHash;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "used_at", nullable = false)
    private OffsetDateTime usedAt;

    @Column(name = "device_identifier", length = 128)
    private String deviceIdentifier;
}
