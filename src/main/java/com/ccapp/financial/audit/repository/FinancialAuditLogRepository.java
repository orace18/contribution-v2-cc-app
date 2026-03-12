package com.ccapp.financial.audit.repository;

import com.ccapp.financial.audit.domain.entity.FinancialAuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FinancialAuditLogRepository extends JpaRepository<FinancialAuditLog, UUID> {

    List<FinancialAuditLog> findByEventTypeOrderByCreatedAtDesc(String eventType, Pageable pageable);
}
