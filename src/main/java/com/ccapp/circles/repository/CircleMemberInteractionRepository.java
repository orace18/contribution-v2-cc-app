package com.ccapp.circles.repository;

import com.ccapp.circles.domain.entity.CircleMemberInteraction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface CircleMemberInteractionRepository extends JpaRepository<CircleMemberInteraction, UUID> {

    boolean existsByCircleIdAndMemberIdAndSenderIdAndCreatedAtBetween(UUID circleId,
                                                                       UUID memberId,
                                                                       UUID senderId,
                                                                       OffsetDateTime start,
                                                                       OffsetDateTime end);
}
