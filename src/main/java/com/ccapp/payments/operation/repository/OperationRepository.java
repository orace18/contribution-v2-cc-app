package com.ccapp.payments.operation.repository;

import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface OperationRepository extends JpaRepository<Operation, UUID> {
    Optional<Operation> findByIdempotencyKey(String idempotencyKey);

    Optional<Operation> findByPspReference(String pspReference);

    Optional<Operation> findByPaymentId(String paymentId);

    Optional<Operation> findByPayoutId(String payoutId);

    Optional<Operation> findByProviderReference(String providerReference);

    Optional<Operation> findByIdAndAccountHolderId(UUID id, UUID accountHolderId);

    List<Operation> findByTypeAndStatusOrderByCreatedAtAsc(OperationType type, OperationStatus status);

    List<Operation> findByStatusOrderByCreatedAtAsc(OperationStatus status);

    List<Operation> findByStatusAndPspStatusAndCreatedAtBeforeOrderByCreatedAtAsc(OperationStatus status,
                                                                                   String pspStatus,
                                                                                   OffsetDateTime createdAtBefore,
                                                                                   Pageable pageable);

    @Query("""
            select o
            from Operation o
            where o.createdAt < :createdAtBefore
              and (o.status = :pendingStatus or o.pspStatus = :reconciliationStatus)
            order by o.createdAt asc
            """)
    List<Operation> findBatchForPspReconciliation(@Param("pendingStatus") OperationStatus pendingStatus,
                                                  @Param("reconciliationStatus") String reconciliationStatus,
                                                  @Param("createdAtBefore") OffsetDateTime createdAtBefore,
                                                  Pageable pageable);

    @Query("""
            select o
            from Operation o
            where o.status = :pendingStatus
              and o.createdAt < :createdAtBefore
              and o.payoutId is null
              and (o.paymentId is not null or o.providerReference is not null or o.pspReference is not null)
            order by o.createdAt asc
            """)
    List<Operation> findPendingPaymentBatchForPspReconciliation(@Param("pendingStatus") OperationStatus pendingStatus,
                                                                @Param("createdAtBefore") OffsetDateTime createdAtBefore,
                                                                Pageable pageable);

    Optional<Operation> findFirstByAccountHolderIdAndTypeAndStatusOrderByCreatedAtDesc(UUID accountHolderId,
                                                                                         OperationType type,
                                                                                         OperationStatus status);

    Optional<Operation> findFirstByAccountHolderIdAndTypeAndPlanIdAndStatusInOrderByCreatedAtDesc(UUID accountHolderId,
                                                                                                       OperationType type,
                                                                                                       UUID planId,
                                                                                                       Collection<OperationStatus> statuses);

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

    @Query("""
            select o
            from Operation o
            where o.accountHolderId = :accountHolderId
               or o.destinationAccountHolderId = :accountHolderId
            order by o.createdAt desc
            """)
    List<Operation> findRecentForAccountHolder(UUID accountHolderId, Pageable pageable);

    @Query("""
            select o
            from Operation o
            where (o.accountHolderId = :accountHolderId
                or o.destinationAccountHolderId = :accountHolderId)
              and o.type in :types
            order by o.createdAt desc
            """)
    List<Operation> findRecentWalletOperationsForAccountHolder(@Param("accountHolderId") UUID accountHolderId,
                                                               @Param("types") Collection<OperationType> types,
                                                               Pageable pageable);
}
