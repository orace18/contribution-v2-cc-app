package com.ccapp.circles.repository;

import com.ccapp.circles.domain.entity.CircleContribution;
import com.ccapp.circles.domain.enums.CircleContributionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface CircleContributionRepository extends JpaRepository<CircleContribution, UUID> {

    long countByCycle_Id(UUID cycleId);

    List<CircleContribution> findByCycle_IdOrderByDueDateAsc(UUID cycleId);

    List<CircleContribution> findByCycle_Circle_IdAndMember_IdOrderByDueDateDesc(UUID circleId, UUID memberId);

    @Query("""
            select count(cc)
            from CircleContribution cc
            where cc.member.accountHolderId = :accountHolderId
              and (cc.status in :statuses or cc.paidAt is not null)
            """)
    long countCompletedByAccountHolderId(UUID accountHolderId, List<CircleContributionStatus> statuses);
}
