package com.ccapp.progress.service;

import com.ccapp.common.exception.NotFoundException;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.domain.enums.DelegatedActionType;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.identity.delegation.service.DelegatedActionService;
import com.ccapp.progress.domain.entity.UserTask;
import com.ccapp.progress.domain.enums.TaskStatus;
import com.ccapp.progress.domain.enums.TaskType;
import com.ccapp.progress.dto.TaskCompletionResponse;
import com.ccapp.progress.dto.TaskResponse;
import com.ccapp.progress.dto.UserProgressResponse;
import com.ccapp.progress.dto.UserTaskResponse;
import com.ccapp.progress.repository.UserTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final UserTaskRepository userTaskRepository;
    private final AccountHolderService accountHolderService;
    private final ProgressService progressService;
    private final AccessControlService accessControlService;
    private final DelegatedActionService delegatedActionService;
    private final TaskEngineService taskEngineService;

    @Transactional
    public List<UserTaskResponse> getUserTasks(UUID actorUserId, UUID accountHolderId) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.VIEW);
        ensureAccountHolderExists(accountHolderId);
        taskEngineService.synchronizeTaskGraph(accountHolderId, false);
        return userTaskRepository.findByAccountHolderIdOrderByCreatedAtAsc(accountHolderId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TaskCompletionResponse completeTask(UUID actorUserId, UUID accountHolderId, TaskType taskType) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.TRANSACT);
        ensureAccountHolderExists(accountHolderId);

        TaskResponse response = taskEngineService.completeTaskByType(accountHolderId, taskType);
        UserTask savedTask = userTaskRepository.findById(response.id())
                .orElseThrow(() -> new NotFoundException("Task not found"));
        UserProgressResponse progress = progressService.recalculateProgress(accountHolderId);
        delegatedActionService.logIfDelegatedActor(
                accountHolderId,
                actorUserId,
                DelegatedActionType.PROGRESS_UPDATED,
                "{\"taskType\":\"" + taskType.name() + "\"}"
        );

        return new TaskCompletionResponse(toResponse(savedTask), progress);
    }

    @Transactional
    public List<TaskResponse> getVisibleTasks(UUID actorUserId, UUID accountHolderId) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.VIEW);
        ensureAccountHolderExists(accountHolderId);
        return taskEngineService.getUserTasks(accountHolderId);
    }

    @Transactional
    public TaskResponse getNextBestAction(UUID actorUserId, UUID accountHolderId) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.VIEW);
        ensureAccountHolderExists(accountHolderId);
        return taskEngineService.getNextBestAction(accountHolderId);
    }

    @Transactional
    public TaskResponse completeTaskById(UUID actorUserId, UUID accountHolderId, UUID taskId) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.TRANSACT);
        ensureAccountHolderExists(accountHolderId);
        UserTask task = userTaskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
        if (!accountHolderId.equals(task.getAccountHolderId())) {
            throw new NotFoundException("Task not found");
        }
        TaskResponse response = taskEngineService.completeTask(taskId);
        progressService.recalculateProgress(accountHolderId);
        delegatedActionService.logIfDelegatedActor(
                accountHolderId,
                actorUserId,
                DelegatedActionType.PROGRESS_UPDATED,
                "{\"taskId\":\"" + taskId + "\"}"
        );
        return response;
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
                task.getCompletedAt(),
                task.getCreatedAt()
        );
    }
}
