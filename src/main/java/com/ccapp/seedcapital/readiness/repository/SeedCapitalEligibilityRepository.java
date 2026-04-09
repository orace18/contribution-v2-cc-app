package com.ccapp.seedcapital.readiness.repository;

import com.ccapp.seedcapital.readiness.domain.entity.SeedCapitalEligibility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SeedCapitalEligibilityRepository extends JpaRepository<SeedCapitalEligibility, UUID> {
}
