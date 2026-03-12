package com.ccapp.payments.commission.repository;

import com.ccapp.payments.commission.domain.entity.CommissionPayoutItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommissionPayoutItemRepository extends JpaRepository<CommissionPayoutItem, UUID> {
    Optional<CommissionPayoutItem> findByBatchIdAndAccountHolderId(UUID batchId, UUID accountHolderId);

    List<CommissionPayoutItem> findByBatchIdOrderByCreatedAtAsc(UUID batchId);
}
