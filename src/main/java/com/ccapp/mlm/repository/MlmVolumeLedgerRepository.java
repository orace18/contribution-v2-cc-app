package com.ccapp.mlm.repository;

import com.ccapp.mlm.domain.entity.MlmVolumeLedger;
import com.ccapp.mlm.repository.projection.MlmWeeklyLegVolumeProjection;
import com.ccapp.mlm.repository.projection.MlmWeeklyVolumeSummaryProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface MlmVolumeLedgerRepository extends JpaRepository<MlmVolumeLedger, UUID> {

    boolean existsBySourceOrderIdAndAccountHolderId(UUID sourceOrderId, UUID accountHolderId);

    @Query("""
            select l.accountHolderId
            from MlmVolumeLedger l
            where l.sourceOrderId = :sourceOrderId
            """)
    Set<UUID> findAccountHolderIdsBySourceOrderId(UUID sourceOrderId);

    List<MlmVolumeLedger> findBySourceOrderIdOrderByAccountHolderIdAsc(UUID sourceOrderId);

    @Query("""
            select l.accountHolderId as accountHolderId,
                   l.legPosition as legPosition,
                   sum(l.volumeCv) as totalVolumeCv
            from MlmVolumeLedger l
            where l.weekId = :weekId
            group by l.accountHolderId, l.legPosition
            """)
    List<MlmWeeklyLegVolumeProjection> aggregateWeeklyVolumeByMemberAndLeg(int weekId);

    @Query("""
            select coalesce(sum(l.volumeCv), 0)
            from MlmVolumeLedger l
            where l.accountHolderId = :accountHolderId
            """)
    BigDecimal sumVolumeByAccountHolderId(UUID accountHolderId);

    @Query("""
            select count(distinct l.sourceMemberId)
            from MlmVolumeLedger l
            where l.accountHolderId = :accountHolderId
              and l.createdAt >= :fromDate
            """)
    long countDistinctSourceMembersByAccountHolderIdSince(UUID accountHolderId, OffsetDateTime fromDate);

    @Query("""
            select distinct l.accountHolderId
            from MlmVolumeLedger l
            where l.weekId = :weekId
            """)
    Set<UUID> findDistinctAccountHolderIdsByWeekId(int weekId);

    @Modifying
    @Query("""
            delete from MlmVolumeLedger l
            where l.sourceOrderId = :sourceOrderId
            """)
    int deleteBySourceOrderId(UUID sourceOrderId);

    @Query("""
            select coalesce(sum(l.volumeCv), 0)
            from MlmVolumeLedger l
            where l.accountHolderId = :accountHolderId
              and l.weekId = :weekId
            """)
    BigDecimal sumVolumeByAccountHolderIdAndWeekId(UUID accountHolderId, int weekId);

    @Query("""
            select count(distinct l.sourceMemberId)
            from MlmVolumeLedger l
            where l.accountHolderId = :accountHolderId
              and l.weekId = :weekId
            """)
    long countDistinctSourceMembersByAccountHolderIdAndWeekId(UUID accountHolderId, int weekId);

    @Query("""
            select l.weekId as weekId,
                   sum(l.volumeCv) as totalVolume,
                   count(l.id) as entriesCount
            from MlmVolumeLedger l
            group by l.weekId
            order by l.weekId desc
            """)
    List<MlmWeeklyVolumeSummaryProjection> summarizeWeeklyVolume(Pageable pageable);
}
