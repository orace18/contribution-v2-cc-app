package com.ccapp.circles.repository;

import com.ccapp.circles.domain.entity.CircleContribution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CircleContributionRepository extends JpaRepository<CircleContribution, UUID> {

    long countByCycle_Id(UUID cycleId);

    List<CircleContribution> findByCycle_IdOrderByDueDateAsc(UUID cycleId);

    List<CircleContribution> findByCycle_Circle_IdAndMember_IdOrderByDueDateDesc(UUID circleId, UUID memberId);
}
