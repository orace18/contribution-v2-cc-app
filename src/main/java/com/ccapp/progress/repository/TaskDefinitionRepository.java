package com.ccapp.progress.repository;

import com.ccapp.progress.domain.entity.TaskDefinition;
import com.ccapp.progress.domain.enums.TaskType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskDefinitionRepository extends JpaRepository<TaskDefinition, UUID> {

    Optional<TaskDefinition> findByTaskTypeAndActiveTrue(TaskType taskType);

    List<TaskDefinition> findAllByOrderByTaskTypeAsc();

    boolean existsByTaskType(TaskType taskType);

    boolean existsByTaskTypeAndIdNot(TaskType taskType, UUID id);
}
