package com.ccapp.agent.repository;

import com.ccapp.agent.domain.entity.AgentProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentProfileRepository extends JpaRepository<AgentProfile, UUID> {

    List<AgentProfile> findByAppUserIdOrderByCreatedAtDesc(UUID appUserId);

    Optional<AgentProfile> findFirstByAppUserIdAndStatusOrderByCreatedAtDesc(UUID appUserId, String status);
}
