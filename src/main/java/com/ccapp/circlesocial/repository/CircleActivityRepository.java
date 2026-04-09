package com.ccapp.circlesocial.repository;

import com.ccapp.circlesocial.domain.entity.CircleActivity;
import com.ccapp.circlesocial.domain.enums.ActivityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface CircleActivityRepository extends JpaRepository<CircleActivity, UUID> {

    List<CircleActivity> findByCircleIdOrderByCreatedAtDesc(UUID circleId);

    long countByCircleId(UUID circleId);

    long countByCircleIdAndActivityType(UUID circleId, ActivityType activityType);

    long countByCircleIdAndCreatedAtAfter(UUID circleId, OffsetDateTime createdAt);

    @Query("""
            select count(distinct ca.actorUserId)
            from CircleActivity ca
            where ca.circleId = :circleId
              and ca.activityType = :activityType
            """)
    long countDistinctActorsByCircleIdAndActivityType(UUID circleId, ActivityType activityType);

    @Query("""
            select ca
            from CircleActivity ca
            where ca.circleId = :circleId
              and (
                  ca.actorUserId = :memberAccountHolderId
                  or ca.referenceId = :memberAccountHolderId
                  or (:memberId is not null and ca.referenceId = :memberId)
              )
            order by ca.createdAt desc
            """)
    List<CircleActivity> findMemberActivityHistory(UUID circleId, UUID memberAccountHolderId, UUID memberId);
}
