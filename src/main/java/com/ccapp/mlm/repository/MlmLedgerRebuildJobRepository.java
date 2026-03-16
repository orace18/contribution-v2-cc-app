package com.ccapp.mlm.repository;

import com.ccapp.mlm.domain.entity.MlmLedgerRebuildJob;
import com.ccapp.mlm.domain.enums.MlmLedgerRebuildJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MlmLedgerRebuildJobRepository extends JpaRepository<MlmLedgerRebuildJob, UUID> {
    Optional<MlmLedgerRebuildJob> findFirstByStatusOrderByRequestedAtAsc(MlmLedgerRebuildJobStatus status);
}
