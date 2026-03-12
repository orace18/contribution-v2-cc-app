package com.ccapp.mlm.repository;

import com.ccapp.mlm.domain.entity.MlmWeeklyBonusJobRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MlmWeeklyBonusJobRunRepository extends JpaRepository<MlmWeeklyBonusJobRun, UUID> {
    Optional<MlmWeeklyBonusJobRun> findByWeekId(Integer weekId);

    Optional<MlmWeeklyBonusJobRun> findTopByOrderByFinishedAtDesc();
}
