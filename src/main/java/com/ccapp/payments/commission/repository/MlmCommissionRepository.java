package com.ccapp.payments.commission.repository;

import com.ccapp.payments.commission.domain.entity.MlmCommission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MlmCommissionRepository extends JpaRepository<MlmCommission, UUID> {
    Optional<MlmCommission> findByOperationId(UUID operationId);
}
