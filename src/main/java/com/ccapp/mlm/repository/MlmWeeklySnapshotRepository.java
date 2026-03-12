package com.ccapp.mlm.repository;

import com.ccapp.mlm.domain.entity.MlmWeeklySnapshot;
import com.ccapp.mlm.domain.entity.MlmWeeklySnapshotId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MlmWeeklySnapshotRepository extends JpaRepository<MlmWeeklySnapshot, MlmWeeklySnapshotId> {

    Optional<MlmWeeklySnapshot> findByWeekIdAndAccountHolderId(Integer weekId, UUID accountHolderId);

    List<MlmWeeklySnapshot> findByWeekIdOrderByTeamVolumeDesc(Integer weekId);

    long countByWeekId(Integer weekId);
}
