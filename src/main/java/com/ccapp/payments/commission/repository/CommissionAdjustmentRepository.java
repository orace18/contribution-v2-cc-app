package com.ccapp.payments.commission.repository;

import com.ccapp.payments.commission.domain.entity.CommissionAdjustment;
import com.ccapp.payments.commission.domain.enums.CommissionAdjustmentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CommissionAdjustmentRepository extends JpaRepository<CommissionAdjustment, UUID> {
    boolean existsByReferenceOrderIdAndAccountHolderIdAndAdjustmentType(UUID referenceOrderId,
                                                                         UUID accountHolderId,
                                                                         CommissionAdjustmentType adjustmentType);

    boolean existsByReferenceOrderIdAndAdjustmentType(UUID referenceOrderId, CommissionAdjustmentType adjustmentType);
}
