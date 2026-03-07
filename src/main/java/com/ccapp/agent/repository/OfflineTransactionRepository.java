package com.ccapp.agent.repository;

import com.ccapp.agent.domain.entity.OfflineTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OfflineTransactionRepository extends JpaRepository<OfflineTransaction, UUID> {

    List<OfflineTransaction> findByAgentUserIdOrderByCreatedAtDesc(UUID agentUserId);

    List<OfflineTransaction> findByAgentUserIdAndStatusOrderByCreatedAtAsc(UUID agentUserId, String status);

    List<OfflineTransaction> findByAgentUserIdAndIdInOrderByCreatedAtAsc(UUID agentUserId, Collection<UUID> ids);
}
