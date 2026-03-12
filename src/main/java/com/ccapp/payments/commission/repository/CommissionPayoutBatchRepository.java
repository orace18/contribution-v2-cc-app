package com.ccapp.payments.commission.repository;

import com.ccapp.payments.commission.domain.entity.CommissionPayoutBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CommissionPayoutBatchRepository extends JpaRepository<CommissionPayoutBatch, UUID> {
    Optional<CommissionPayoutBatch> findByWeekId(Integer weekId);

    Page<CommissionPayoutBatch> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
