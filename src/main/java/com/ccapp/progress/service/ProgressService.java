package com.ccapp.progress.service;

import com.ccapp.common.exception.NotFoundException;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.progress.domain.entity.UserProgress;
import com.ccapp.progress.domain.entity.UserTask;
import com.ccapp.progress.domain.enums.TaskStatus;
import com.ccapp.progress.domain.enums.TaskType;
import com.ccapp.progress.dto.TaskResponse;
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
    private final TaskEngineService taskEngineService;

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
        taskEngineService.synchronizeTaskGraph(accountHolderId, false);

        int totalTasks = TaskType.progressTypes().size();
        long completedTasks = userTaskRepository.findByAccountHolderIdOrderByCreatedAtAsc(accountHolderId).stream()
                .filter(task -> task.getStatus() == TaskStatus.COMPLETED)
                .map(UserTask::getTaskType)
                .filter(TaskType::progressTracked)
                .distinct()
                .count();
        BigDecimal progress = totalTasks == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(completedTasks * 100L)
                .divide(BigDecimal.valueOf(totalTasks), 2, RoundingMode.HALF_UP);

        TaskType currentStep = userTaskRepository.findByAccountHolderIdOrderByCreatedAtAsc(accountHolderId).stream()
                .filter(task -> task.getStatus() == TaskStatus.COMPLETED)
                .filter(task -> task.getTaskType().progressTracked())
                .sorted((left, right) -> {
                    if (left.getCompletedAt() == null && right.getCompletedAt() == null) {
                        return 0;
                    }
                    if (left.getCompletedAt() == null) {
                        return 1;
                    }
                    if (right.getCompletedAt() == null) {
                        return -1;
                    }
                    return right.getCompletedAt().compareTo(left.getCompletedAt());
                })
                .map(UserTask::getTaskType)
                .findFirst()
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
        TaskResponse nextBestAction = taskEngineService.getNextBestAction(accountHolderId);
        return nextBestAction == null ? null : nextBestAction.type();
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
