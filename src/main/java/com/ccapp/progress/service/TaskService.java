package com.ccapp.progress.service;

import com.ccapp.common.exception.NotFoundException;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.domain.enums.DelegatedActionType;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.identity.delegation.service.DelegatedActionService;
import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.notifications.domain.enums.NotificationChannel;
import com.ccapp.notifications.domain.enums.NotificationType;
import com.ccapp.notifications.event.NotificationRequestedEvent;
import com.ccapp.progress.domain.entity.UserTask;
import com.ccapp.progress.domain.enums.TaskStatus;
import com.ccapp.progress.domain.enums.TaskType;
import com.ccapp.progress.dto.TaskCompletionResponse;
import com.ccapp.progress.dto.UserProgressResponse;
import com.ccapp.progress.dto.UserTaskResponse;
import com.ccapp.progress.repository.UserTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TaskService {

    private final UserTaskRepository userTaskRepository;
    private final AccountHolderService accountHolderService;
    private final ProgressService progressService;
    private final DomainEventPublisher domainEventPublisher;
    private final AccessControlService accessControlService;
    private final DelegatedActionService delegatedActionService;

    @Transactional(readOnly = true)
    public List<UserTaskResponse> getUserTasks(UUID actorUserId, UUID accountHolderId) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.VIEW);
        ensureAccountHolderExists(accountHolderId);

        Map<TaskType, UserTask> taskByType = new EnumMap<>(TaskType.class);
        for (UserTask task : userTaskRepository.findByAccountHolderIdOrderByCreatedAtAsc(accountHolderId)) {
            taskByType.put(task.getTaskType(), task);
        }

        return Arrays.stream(TaskType.values())
                .map(taskType -> {
                    UserTask task = taskByType.get(taskType);
                    return task == null
                            ? new UserTaskResponse(null, taskType, TaskStatus.PENDING, null, null)
                            : toResponse(task);
                })
                .toList();
    }

    @Transactional
    public TaskCompletionResponse completeTask(UUID actorUserId, UUID accountHolderId, TaskType taskType) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.TRANSACT);
        ensureAccountHolderExists(accountHolderId);

        UserTask task = userTaskRepository.findByAccountHolderIdAndTaskType(accountHolderId, taskType)
                .orElseGet(() -> UserTask.builder()
                        .accountHolderId(accountHolderId)
                        .taskType(taskType)
                        .status(TaskStatus.PENDING)
                        .createdAt(OffsetDateTime.now())
                        .build());

        task.setStatus(TaskStatus.COMPLETED);
        task.setValidatedAt(OffsetDateTime.now());

        UserTask savedTask = userTaskRepository.save(task);
        UserProgressResponse progress = progressService.recalculateProgress(accountHolderId);
        emitTaskCompletedNotification(accountHolderId, taskType);
        delegatedActionService.logIfDelegatedActor(
                accountHolderId,
                actorUserId,
                DelegatedActionType.PROGRESS_UPDATED,
                "{\"taskType\":\"" + taskType.name() + "\"}"
        );

        return new TaskCompletionResponse(toResponse(savedTask), progress);
    }

    private void emitTaskCompletedNotification(UUID accountHolderId, TaskType taskType) {
        try {
            domainEventPublisher.publish(NotificationRequestedEvent.create(
                    accountHolderId,
                    null,
                    NotificationType.TASK_COMPLETED,
                    NotificationChannel.IN_APP,
                    "TASK_COMPLETED",
                    "Task completed: " + taskType.name(),
                    null,
                    true
            ));
        } catch (RuntimeException ex) {
            log.warn("Failed to emit TASK_COMPLETED notification for accountHolderId={}", accountHolderId, ex);
        }
    }

    private void ensureAccountHolderExists(UUID accountHolderId) {
        try {
            accountHolderService.getById(accountHolderId);
        } catch (NotFoundException ex) {
            throw new NotFoundException("Account holder not found");
        }
    }

    private UserTaskResponse toResponse(UserTask task) {
        return new UserTaskResponse(
                task.getId(),
                task.getTaskType(),
                task.getStatus(),
                task.getValidatedAt(),
                task.getCreatedAt()
        );
    }
}
