package com.ccapp.financing.repository;

import com.ccapp.financing.domain.entity.PartnerSettlementBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PartnerSettlementBatchRepository extends JpaRepository<PartnerSettlementBatch, UUID> {

    Page<PartnerSettlementBatch> findByPartnerIdOrderByCreatedAtDesc(UUID partnerId, Pageable pageable);
}
