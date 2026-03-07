package com.ccapp.identity.delegation.domain.entity;

import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
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
@Table(name = "account_delegations", indexes = {
        @Index(name = "idx_account_delegations_account_holder_id", columnList = "account_holder_id"),
        @Index(name = "idx_account_delegations_delegate_user_id", columnList = "delegate_user_id")
})
public class AccountDelegation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_holder_id", nullable = false)
    private UUID accountHolderId;

    @Column(name = "delegate_user_id", nullable = false)
    private UUID delegateUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission_level", nullable = false, length = 16)
    private PermissionLevel permissionLevel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
