package com.ccapp.circles.repository;

import com.ccapp.circles.domain.entity.CircleLoan;
import com.ccapp.circles.domain.enums.CircleLoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CircleLoanRepository extends JpaRepository<CircleLoan, UUID> {

    boolean existsByBorrower_IdAndStatusIn(UUID borrowerMemberId, Collection<CircleLoanStatus> statuses);

    List<CircleLoan> findByCycle_Circle_IdOrderByApprovedAtDesc(UUID circleId);

    @Query("""
            select cl
            from CircleLoan cl
            where cl.cycle.circle.id = :circleId
              and cl.borrower.id = :borrowerMemberId
            order by coalesce(cl.disbursedAt, cl.approvedAt) desc, cl.id desc
            """)
    List<CircleLoan> findMemberLoansByCircleOrderByTimelineDesc(UUID circleId, UUID borrowerMemberId);

    default Optional<CircleLoan> findLatestMemberLoanByCircle(UUID circleId, UUID borrowerMemberId) {
        List<CircleLoan> loans = findMemberLoansByCircleOrderByTimelineDesc(circleId, borrowerMemberId);
        if (loans.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(loans.get(0));
    }
}
