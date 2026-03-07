package com.ccapp.ledger.reconciliation.repository;

import com.ccapp.ledger.reconciliation.domain.entity.ReconciliationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReconciliationRecordRepository extends JpaRepository<ReconciliationRecord, UUID> {
    List<ReconciliationRecord> findByBatchId(UUID batchId);
}
