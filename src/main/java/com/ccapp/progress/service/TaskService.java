package com.ccapp.progress.service;

import com.ccapp.common.exception.NotFoundException;
import com.ccapp.identity.repository.UserRepository;
import com.ccapp.progress.domain.entity.UserTask;
import com.ccapp.progress.domain.enums.TaskStatus;
import com.ccapp.progress.domain.enums.TaskType;
import com.ccapp.progress.dto.TaskCompletionResponse;
import com.ccapp.progress.dto.UserProgressResponse;
import com.ccapp.progress.dto.UserTaskResponse;
import com.ccapp.progress.repository.UserTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final UserTaskRepository userTaskRepository;
    private final UserRepository userRepository;
    private final ProgressService progressService;

    @Transactional(readOnly = true)
    public List<UserTaskResponse> getUserTasks(UUID userId) {
        ensureUserExists(userId);

        Map<TaskType, UserTask> taskByType = new EnumMap<>(TaskType.class);
        for (UserTask task : userTaskRepository.findByUserIdOrderByCreatedAtAsc(userId)) {
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
    public TaskCompletionResponse completeTask(UUID userId, TaskType taskType) {
        ensureUserExists(userId);

        UserTask task = userTaskRepository.findByUserIdAndTaskType(userId, taskType)
                .orElseGet(() -> UserTask.builder()
                        .userId(userId)
                        .taskType(taskType)
                        .status(TaskStatus.PENDING)
                        .createdAt(OffsetDateTime.now())
                        .build());

        task.setStatus(TaskStatus.COMPLETED);
        task.setValidatedAt(OffsetDateTime.now());

        UserTask savedTask = userTaskRepository.save(task);
        UserProgressResponse progress = progressService.recalculateProgress(userId);

        return new TaskCompletionResponse(toResponse(savedTask), progress);
    }

    private void ensureUserExists(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User not found");
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
