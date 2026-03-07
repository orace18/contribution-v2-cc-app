package com.ccapp.circlesocial.repository;

import com.ccapp.circlesocial.domain.entity.CircleActivity;
import com.ccapp.circlesocial.domain.enums.ActivityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface CircleActivityRepository extends JpaRepository<CircleActivity, UUID> {

    List<CircleActivity> findByCircleIdOrderByCreatedAtDesc(UUID circleId);

    long countByCircleId(UUID circleId);

    long countByCircleIdAndActivityType(UUID circleId, ActivityType activityType);

    @Query("""
            select count(distinct ca.actorUserId)
            from CircleActivity ca
            where ca.circleId = :circleId
              and ca.activityType = :activityType
            """)
    long countDistinctActorsByCircleIdAndActivityType(UUID circleId, ActivityType activityType);
}
