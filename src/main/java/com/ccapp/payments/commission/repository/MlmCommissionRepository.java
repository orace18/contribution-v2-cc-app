package com.ccapp.payments.commission.repository;

import com.ccapp.payments.commission.domain.entity.MlmCommission;
import com.ccapp.payments.commission.domain.enums.CommissionStatus;
import com.ccapp.payments.commission.domain.enums.CommissionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface MlmCommissionRepository extends JpaRepository<MlmCommission, UUID> {
    Optional<MlmCommission> findByOperationId(UUID operationId);

    Optional<MlmCommission> findByBeneficiaryAccountHolderIdAndSourceAndCommissionType(UUID beneficiaryAccountHolderId,
                                                                                         String source,
                                                                                         CommissionType commissionType);

    Optional<MlmCommission> findByBeneficiaryAccountHolderIdAndCommissionTypeAndWeekId(UUID beneficiaryAccountHolderId,
                                                                                         CommissionType commissionType,
                                                                                         Integer weekId);

    long countByCommissionTypeAndWeekId(CommissionType commissionType, Integer weekId);

    @Query("""
            select c.beneficiaryAccountHolderId
            from MlmCommission c
            where c.commissionType = :commissionType
              and c.weekId = :weekId
            """)
    Set<UUID> findBeneficiaryIdsByCommissionTypeAndWeekId(CommissionType commissionType, Integer weekId);

    List<MlmCommission> findByWeekIdAndCommissionTypeAndStatusOrderByBeneficiaryAccountHolderIdAsc(Integer weekId,
                                                                                                     CommissionType commissionType,
                                                                                                     CommissionStatus status);

    List<MlmCommission> findByWeekIdAndCommissionTypeAndBeneficiaryAccountHolderIdAndStatusOrderByCreatedAtAsc(
            Integer weekId,
            CommissionType commissionType,
            UUID beneficiaryAccountHolderId,
            CommissionStatus status
    );

    @Query("""
            select coalesce(sum(c.amount), 0)
            from MlmCommission c
            where c.beneficiaryAccountHolderId = :accountHolderId
            """)
    BigDecimal sumAmountByBeneficiaryAccountHolderId(UUID accountHolderId);

    @Query("""
            select coalesce(sum(c.amount), 0)
            from MlmCommission c
            where c.beneficiaryAccountHolderId = :accountHolderId
              and c.weekId = :weekId
              and c.commissionType = :commissionType
            """)
    BigDecimal sumAmountByBeneficiaryAccountHolderIdAndWeekIdAndCommissionType(UUID accountHolderId,
                                                                                Integer weekId,
                                                                                CommissionType commissionType);

    @Query("""
            select coalesce(sum(c.amount), 0)
            from MlmCommission c
            where c.beneficiaryAccountHolderId = :accountHolderId
              and c.weekId = :weekId
              and c.commissionType = :commissionType
              and c.status = :status
            """)
    BigDecimal sumAmountByBeneficiaryAccountHolderIdAndWeekIdAndCommissionTypeAndStatus(UUID accountHolderId,
                                                                                          Integer weekId,
                                                                                          CommissionType commissionType,
                                                                                          CommissionStatus status);

    @Query("""
            select distinct c.beneficiaryAccountHolderId
            from MlmCommission c
            where c.weekId = :weekId
              and c.commissionType = :commissionType
            """)
    Set<UUID> findDistinctBeneficiaryAccountHolderIdsByWeekIdAndCommissionType(Integer weekId,
                                                                                CommissionType commissionType);

    @Query("""
            select c
            from MlmCommission c
            where (:weekId is null or c.weekId = :weekId)
              and (:commissionType is null or c.commissionType = :commissionType)
              and (:commissionStatus is null or c.status = :commissionStatus)
            order by c.createdAt desc
            """)
    Page<MlmCommission> searchForAdmin(Integer weekId,
                                       CommissionType commissionType,
                                       CommissionStatus commissionStatus,
                                       Pageable pageable);

    @Query("""
            select count(c)
            from MlmCommission c
            where c.createdAt >= :fromDate
              and c.amount >= :thresholdAmount
            """)
    long countPotentialSpikesSince(OffsetDateTime fromDate, BigDecimal thresholdAmount);
}
