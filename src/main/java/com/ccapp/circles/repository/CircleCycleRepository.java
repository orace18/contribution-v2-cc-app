package com.ccapp.circles.repository;

import com.ccapp.circles.domain.entity.CircleCycle;
import com.ccapp.circles.domain.enums.CircleCycleStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CircleCycleRepository extends JpaRepository<CircleCycle, UUID> {

    Optional<CircleCycle> findByCircle_IdAndStatus(UUID circleId, CircleCycleStatus status);

    boolean existsByCircle_IdAndStatus(UUID circleId, CircleCycleStatus status);

    List<CircleCycle> findByCircle_IdOrderByCycleNumberDesc(UUID circleId);

    Optional<CircleCycle> findTopByCircle_IdOrderByCycleNumberDesc(UUID circleId);
}
