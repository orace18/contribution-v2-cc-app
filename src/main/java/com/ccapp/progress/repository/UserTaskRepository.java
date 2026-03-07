package com.ccapp.progress.repository;

import com.ccapp.progress.domain.entity.UserTask;
import com.ccapp.progress.domain.enums.TaskStatus;
import com.ccapp.progress.domain.enums.TaskType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserTaskRepository extends JpaRepository<UserTask, UUID> {

    Optional<UserTask> findByUserIdAndTaskType(UUID userId, TaskType taskType);

    List<UserTask> findByUserIdOrderByCreatedAtAsc(UUID userId);

    long countByUserIdAndStatus(UUID userId, TaskStatus status);

    Optional<UserTask> findTopByUserIdAndStatusOrderByValidatedAtDesc(UUID userId, TaskStatus status);
}
