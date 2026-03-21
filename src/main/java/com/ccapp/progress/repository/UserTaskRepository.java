package com.ccapp.progress.repository;

import com.ccapp.progress.domain.entity.UserTask;
import com.ccapp.progress.domain.enums.TaskStatus;
import com.ccapp.progress.domain.enums.TaskType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserTaskRepository extends JpaRepository<UserTask, UUID> {

    Optional<UserTask> findByAccountHolderIdAndTaskType(UUID accountHolderId, TaskType taskType);

    List<UserTask> findByAccountHolderIdOrderByCreatedAtAsc(UUID accountHolderId);

    long countByAccountHolderIdAndStatus(UUID accountHolderId, TaskStatus status);

    Optional<UserTask> findTopByAccountHolderIdAndStatusOrderByCompletedAtDesc(UUID accountHolderId, TaskStatus status);

    List<UserTask> findByAccountHolderIdAndTaskTypeOrderByCreatedAtDesc(UUID accountHolderId, TaskType taskType);

    Optional<UserTask> findTopByAccountHolderIdAndTaskTypeAndStatusInOrderByCreatedAtDesc(UUID accountHolderId,
                                                                                           TaskType taskType,
                                                                                           Collection<TaskStatus> statuses);

    boolean existsByAccountHolderIdAndTaskTypeAndStatus(UUID accountHolderId, TaskType taskType, TaskStatus status);

    @Query("""
            select t
            from UserTask t
            where t.accountHolderId = :accountHolderId
              and t.status in :statuses
            order by t.priorityScore desc,
                     case when t.dueDate is null then 1 else 0 end asc,
                     t.dueDate asc,
                     t.createdAt asc
            """)
    List<UserTask> findRankedByAccountHolderIdAndStatuses(UUID accountHolderId, Collection<TaskStatus> statuses);

    @Query("""
            select t
            from UserTask t
            where t.accountHolderId = :accountHolderId
              and t.status in :statuses
              and t.dueDate is not null
              and t.dueDate < :threshold
            """)
    List<UserTask> findOverdueTasks(UUID accountHolderId, Collection<TaskStatus> statuses, OffsetDateTime threshold);
}
