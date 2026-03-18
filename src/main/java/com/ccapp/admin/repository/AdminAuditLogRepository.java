package com.ccapp.admin.repository;

import com.ccapp.admin.audit.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {

    Optional<AdminAuditLog> findFirstByEntityTypeAndEntityIdOrderByTimestampDesc(String entityType, String entityId);

    long countByEntityTypeAndEntityIdAndAction(String entityType, String entityId, String action);
}
