package com.ccapp.progress.service;

import com.ccapp.progress.domain.entity.TaskAuditLog;
import com.ccapp.progress.domain.entity.UserTask;
import com.ccapp.progress.repository.TaskAuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskAuditService {

    private final TaskAuditLogRepository taskAuditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void record(UserTask task, String action, Map<String, ?> details) {
        if (task == null) {
            return;
        }
        taskAuditLogRepository.save(TaskAuditLog.builder()
                .userTaskId(task.getId())
                .accountHolderId(task.getAccountHolderId())
                .taskType(task.getTaskType().name())
                .action(action)
                .detailsJson(writeJson(details))
                .build());
    }

    @Transactional
    public void record(UUID accountHolderId, String taskType, String action, Map<String, ?> details) {
        if (accountHolderId == null || taskType == null) {
            return;
        }
        taskAuditLogRepository.save(TaskAuditLog.builder()
                .accountHolderId(accountHolderId)
                .taskType(taskType)
                .action(action)
                .detailsJson(writeJson(details))
                .build());
    }

    private String writeJson(Map<String, ?> details) {
        if (details == null || details.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
