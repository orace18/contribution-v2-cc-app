package com.ccapp.seedcapital.readiness.repository;

import com.ccapp.seedcapital.readiness.domain.entity.CircleTrustScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CircleTrustScoreRepository extends JpaRepository<CircleTrustScore, UUID> {
}
