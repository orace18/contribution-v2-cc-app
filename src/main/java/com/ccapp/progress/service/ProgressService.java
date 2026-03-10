package com.ccapp.progress.service;

import com.ccapp.common.exception.NotFoundException;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.progress.domain.entity.UserProgress;
import com.ccapp.progress.domain.entity.UserTask;
import com.ccapp.progress.domain.enums.TaskStatus;
import com.ccapp.progress.domain.enums.TaskType;
import com.ccapp.progress.dto.UserProgressResponse;
import com.ccapp.progress.repository.UserProgressRepository;
import com.ccapp.progress.repository.UserTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProgressService {

    private final UserProgressRepository userProgressRepository;
    private final UserTaskRepository userTaskRepository;
    private final AccountHolderService accountHolderService;
    private final AccessControlService accessControlService;

    @Transactional
    public UserProgressResponse getUserProgress(UUID actorUserId, UUID accountHolderId) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.VIEW);
        return recalculateProgress(accountHolderId);
    }

    @Transactional
    public UserProgressResponse getUserProgress(UUID accountHolderId) {
        return recalculateProgress(accountHolderId);
    }

    @Transactional
    public UserProgressResponse recalculateProgress(UUID accountHolderId) {
        ensureAccountHolderExists(accountHolderId);

        long completedTasks = userTaskRepository.countByAccountHolderIdAndStatus(accountHolderId, TaskStatus.COMPLETED);
        int totalTasks = TaskType.values().length;
        BigDecimal progress = BigDecimal.valueOf(completedTasks * 100L)
                .divide(BigDecimal.valueOf(totalTasks), 2, RoundingMode.HALF_UP);

        TaskType currentStep = userTaskRepository.findTopByAccountHolderIdAndStatusOrderByValidatedAtDesc(accountHolderId, TaskStatus.COMPLETED)
                .map(UserTask::getTaskType)
                .orElse(null);

        TaskType nextTask = resolveNextTask(accountHolderId);

        UserProgress userProgress = userProgressRepository.findById(accountHolderId)
                .orElseGet(() -> UserProgress.builder().accountHolderId(accountHolderId).build());

        userProgress.setCurrentStep(currentStep);
        userProgress.setProgressPercentage(progress);
        userProgress.setNextTask(nextTask);

        UserProgress saved = userProgressRepository.save(userProgress);
        return toResponse(saved);
    }

    private TaskType resolveNextTask(UUID accountHolderId) {
        Map<TaskType, TaskStatus> statusByTask = userTaskRepository.findByAccountHolderIdOrderByCreatedAtAsc(accountHolderId).stream()
                .collect(Collectors.toMap(UserTask::getTaskType, UserTask::getStatus, (left, right) -> right));

        for (TaskType taskType : TaskType.values()) {
            TaskStatus status = statusByTask.get(taskType);
            if (status != TaskStatus.COMPLETED) {
                return taskType;
            }
        }
        return null;
    }

    private void ensureAccountHolderExists(UUID accountHolderId) {
        try {
            accountHolderService.getById(accountHolderId);
        } catch (NotFoundException ex) {
            throw new NotFoundException("Account holder not found");
        }
    }

    private UserProgressResponse toResponse(UserProgress progress) {
        return new UserProgressResponse(
                progress.getAccountHolderId(),
                progress.getCurrentStep(),
                progress.getProgressPercentage(),
                progress.getNextTask(),
                progress.getUpdatedAt()
        );
    }
}
