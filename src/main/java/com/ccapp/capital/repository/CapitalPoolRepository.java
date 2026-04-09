package com.ccapp.capital.repository;

import com.ccapp.capital.domain.entity.CapitalPool;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Deprecated(since = "2026-04", forRemoval = false)
public interface CapitalPoolRepository extends JpaRepository<CapitalPool, UUID> {

    Optional<CapitalPool> findByFund_IdAndCircleIdAndCountryCode(UUID fundId, UUID circleId, String countryCode);

    List<CapitalPool> findByCircleIdOrderByIdDesc(UUID circleId);

    List<CapitalPool> findByCountryCodeOrderByIdDesc(String countryCode);

    List<CapitalPool> findByCircleIdAndCountryCodeOrderByIdDesc(UUID circleId, String countryCode);
}
