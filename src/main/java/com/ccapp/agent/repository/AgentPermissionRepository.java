package com.ccapp.agent.repository;

import com.ccapp.agent.domain.entity.AgentPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentPermissionRepository extends JpaRepository<AgentPermission, UUID> {

    List<AgentPermission> findByAgentProfile_IdOrderByCreatedAtAsc(UUID agentProfileId);
}
