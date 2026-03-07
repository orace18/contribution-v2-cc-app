package com.ccapp.progress.service;

import com.ccapp.common.exception.NotFoundException;
import com.ccapp.identity.repository.UserRepository;
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
    private final UserRepository userRepository;

    @Transactional
    public UserProgressResponse getUserProgress(UUID userId) {
        return recalculateProgress(userId);
    }

    @Transactional
    public UserProgressResponse recalculateProgress(UUID userId) {
        ensureUserExists(userId);

        long completedTasks = userTaskRepository.countByUserIdAndStatus(userId, TaskStatus.COMPLETED);
        int totalTasks = TaskType.values().length;
        BigDecimal progress = BigDecimal.valueOf(completedTasks * 100L)
                .divide(BigDecimal.valueOf(totalTasks), 2, RoundingMode.HALF_UP);

        TaskType currentStep = userTaskRepository.findTopByUserIdAndStatusOrderByValidatedAtDesc(userId, TaskStatus.COMPLETED)
                .map(UserTask::getTaskType)
                .orElse(null);

        TaskType nextTask = resolveNextTask(userId);

        UserProgress userProgress = userProgressRepository.findById(userId)
                .orElseGet(() -> UserProgress.builder().userId(userId).build());

        userProgress.setCurrentStep(currentStep);
        userProgress.setProgressPercentage(progress);
        userProgress.setNextTask(nextTask);

        UserProgress saved = userProgressRepository.save(userProgress);
        return toResponse(saved);
    }

    private TaskType resolveNextTask(UUID userId) {
        Map<TaskType, TaskStatus> statusByTask = userTaskRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .collect(Collectors.toMap(UserTask::getTaskType, UserTask::getStatus, (left, right) -> right));

        for (TaskType taskType : TaskType.values()) {
            TaskStatus status = statusByTask.get(taskType);
            if (status != TaskStatus.COMPLETED) {
                return taskType;
            }
        }
        return null;
    }

    private void ensureUserExists(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User not found");
        }
    }

    private UserProgressResponse toResponse(UserProgress progress) {
        return new UserProgressResponse(
                progress.getUserId(),
                progress.getCurrentStep(),
                progress.getProgressPercentage(),
                progress.getNextTask(),
                progress.getUpdatedAt()
        );
    }
}
