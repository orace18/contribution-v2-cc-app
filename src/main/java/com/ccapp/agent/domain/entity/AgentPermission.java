package com.ccapp.agent.domain.entity;

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
@Table(name = "agent_permissions", indexes = {
        @Index(name = "idx_agent_permissions_profile_id", columnList = "agent_profile_id")
})
public class AgentPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_profile_id", nullable = false, foreignKey = @ForeignKey(name = "fk_agent_permissions_profile"))
    private AgentProfile agentProfile;

    @Column(name = "permission", nullable = false, length = 32)
    private String permission;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
