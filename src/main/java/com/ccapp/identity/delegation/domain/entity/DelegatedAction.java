package com.ccapp.identity.delegation.domain.entity;

import com.ccapp.identity.delegation.domain.enums.DelegatedActionType;
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
@Table(name = "delegated_actions", indexes = {
        @Index(name = "idx_delegated_actions_account_holder_created_at", columnList = "account_holder_id, created_at"),
        @Index(name = "idx_delegated_actions_actor_user_created_at", columnList = "actor_user_id, created_at")
})
public class DelegatedAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_holder_id", nullable = false)
    private UUID accountHolderId;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 32)
    private DelegatedActionType actionType;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
