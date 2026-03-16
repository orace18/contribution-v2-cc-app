package com.ccapp.circles.repository;

import com.ccapp.circles.domain.entity.CircleLoan;
import com.ccapp.circles.domain.enums.CircleLoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CircleLoanRepository extends JpaRepository<CircleLoan, UUID> {

    boolean existsByBorrower_IdAndStatusIn(UUID borrowerMemberId, Collection<CircleLoanStatus> statuses);

    List<CircleLoan> findByCycle_Circle_IdOrderByApprovedAtDesc(UUID circleId);
}
