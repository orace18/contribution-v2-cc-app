package com.ccapp.mlm.repository;

import com.ccapp.mlm.domain.entity.MlmPlacementAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MlmPlacementAuditLogRepository extends JpaRepository<MlmPlacementAuditLog, UUID> {

    Optional<MlmPlacementAuditLog> findFirstByAccountHolderIdOrderByCreatedAtDesc(UUID accountHolderId);
}
