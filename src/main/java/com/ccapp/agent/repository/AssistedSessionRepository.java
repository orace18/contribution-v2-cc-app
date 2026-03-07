package com.ccapp.agent.repository;

import com.ccapp.agent.domain.entity.AssistedSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssistedSessionRepository extends JpaRepository<AssistedSession, UUID> {

    List<AssistedSession> findByAgentUserIdOrderByStartedAtDesc(UUID agentUserId);
}
