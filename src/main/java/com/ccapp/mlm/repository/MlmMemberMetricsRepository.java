package com.ccapp.mlm.repository;

import com.ccapp.mlm.domain.entity.MlmMemberMetrics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MlmMemberMetricsRepository extends JpaRepository<MlmMemberMetrics, UUID> {
}
