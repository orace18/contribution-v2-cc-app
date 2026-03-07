package com.ccapp.agent.domain.entity;

import com.ccapp.agent.domain.enums.SessionType;
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
@Table(name = "assisted_sessions", indexes = {
        @Index(name = "idx_assisted_sessions_agent_user_id", columnList = "agent_user_id"),
        @Index(name = "idx_assisted_sessions_account_holder_id", columnList = "account_holder_id")
})
public class AssistedSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agent_user_id", nullable = false)
    private UUID agentUserId;

    @Column(name = "account_holder_id", nullable = false)
    private UUID accountHolderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false, length = 16)
    private SessionType sessionType;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) {
            startedAt = OffsetDateTime.now();
        }
    }
}
