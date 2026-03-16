package com.ccapp.financing.repository;

import com.ccapp.financing.domain.entity.PartnerSettlementItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PartnerSettlementItemRepository extends JpaRepository<PartnerSettlementItem, UUID> {

    List<PartnerSettlementItem> findByBatchIdOrderByCreatedAtAsc(UUID batchId);

    boolean existsByApplicationId(UUID applicationId);
}
