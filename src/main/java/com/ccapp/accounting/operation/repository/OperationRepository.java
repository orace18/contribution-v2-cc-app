package com.ccapp.accounting.operation.repository;

import com.ccapp.accounting.operation.domain.entity.Operation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OperationRepository extends JpaRepository<Operation, UUID> {
    Optional<Operation> findByIdempotencyKey(String idempotencyKey);

    Optional<Operation> findByPspReference(String pspReference);
}
