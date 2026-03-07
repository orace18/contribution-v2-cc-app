package com.ccapp.capital.repository;

import com.ccapp.capital.domain.entity.CapitalPool;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CapitalPoolRepository extends JpaRepository<CapitalPool, UUID> {

    Optional<CapitalPool> findByFund_IdAndCircleId(UUID fundId, UUID circleId);

    List<CapitalPool> findByCircleIdOrderByIdDesc(UUID circleId);
}
