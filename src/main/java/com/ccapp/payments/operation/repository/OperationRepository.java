package com.ccapp.payments.operation.repository;

import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OperationRepository extends JpaRepository<Operation, UUID> {
    Optional<Operation> findByIdempotencyKey(String idempotencyKey);

    Optional<Operation> findByPspReference(String pspReference);

    Optional<Operation> findFirstByAccountHolderIdAndTypeAndStatusOrderByCreatedAtDesc(UUID accountHolderId,
                                                                                         OperationType type,
                                                                                         OperationStatus status);

    long countByType(OperationType type);

    long countByTypeAndStatus(OperationType type, OperationStatus status);

    @Query("""
            select o
            from Operation o
            where o.type = :type
              and o.status = :status
              and o.createdAt = (
                  select max(o2.createdAt)
                  from Operation o2
                  where o2.accountHolderId = o.accountHolderId
                    and o2.type = :type
                    and o2.status = :status
              )
            """)
    List<Operation> findLatestByTypeAndStatus(OperationType type, OperationStatus status);
}
