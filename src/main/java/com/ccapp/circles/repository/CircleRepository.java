package com.ccapp.circles.repository;

import com.ccapp.circles.domain.entity.Circle;
import com.ccapp.circles.domain.enums.CircleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CircleRepository extends JpaRepository<Circle, UUID> {

    Optional<Circle> findByIdAndStatus(UUID id, CircleStatus status);

    @Query("""
            select (count(c) > 0) from Circle c
            where c.status = com.ccapp.circles.domain.enums.CircleStatus.ACTIVE
              and exists (
                  select 1 from CircleMember cm
                  where cm.circle = c
                    and cm.userId = :userId
              )
            """)
    boolean existsActiveCircleForUser(UUID userId);

    @Query("""
            select c from Circle c
            where c.status = com.ccapp.circles.domain.enums.CircleStatus.ACTIVE
              and exists (
                  select 1 from CircleMember cm
                  where cm.circle = c
                    and cm.userId = :userId
              )
            order by c.createdAt desc
            """)
    List<Circle> findActiveCirclesForUser(UUID userId);
}
