package com.ccapp.circles.repository;

import com.ccapp.circles.domain.entity.CircleMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CircleMemberRepository extends JpaRepository<CircleMember, UUID> {

    boolean existsByCircle_IdAndUserId(UUID circleId, UUID userId);

    long countByCircle_Id(UUID circleId);

    List<CircleMember> findByCircle_IdOrderByJoinedAtAsc(UUID circleId);
}
