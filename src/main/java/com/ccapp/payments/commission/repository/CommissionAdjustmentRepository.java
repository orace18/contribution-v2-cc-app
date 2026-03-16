package com.ccapp.payments.commission.repository;

import com.ccapp.payments.commission.domain.entity.CommissionAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CommissionAdjustmentRepository extends JpaRepository<CommissionAdjustment, UUID> {
    List<CommissionAdjustment> findByReferenceOrderIdOrderByCreatedAtAsc(UUID referenceOrderId);
}
