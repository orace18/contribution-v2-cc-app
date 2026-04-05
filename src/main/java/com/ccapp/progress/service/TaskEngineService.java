package com.ccapp.progress.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.notifications.domain.enums.NotificationChannel;
import com.ccapp.notifications.domain.enums.NotificationType;
import com.ccapp.notifications.dto.CreateNotificationRequest;
import com.ccapp.notifications.dto.SendMultiChannelNotificationRequest;
import com.ccapp.notifications.service.NotificationService;
import com.ccapp.notifications.template.NotificationTemplateRenderer;
import com.ccapp.progress.domain.entity.TaskDefinition;
import com.ccapp.progress.domain.entity.UserTask;
import com.ccapp.progress.domain.enums.TaskStatus;
import com.ccapp.progress.domain.enums.TaskType;
import com.ccapp.progress.dto.TaskResponse;
import com.ccapp.progress.repository.TaskDefinitionRepository;
import com.ccapp.progress.repository.UserTaskRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskEngineService {

    private final UserTaskRepository userTaskRepository;
    private final TaskDefinitionRepository taskDefinitionRepository;
    private final TaskStateResolver taskStateResolver;
    private final TaskAuditService taskAuditService;
    private final NotificationService notificationService;
    private final NotificationTemplateRenderer templateRenderer;
    private final ObjectMapper objectMapper;

    @Transactional
    public TaskResponse createTask(UUID accountHolderId, TaskType taskType, JsonNode payload) {
        UserTask existingTask = findActiveTask(accountHolderId, taskType);
        UserTask task = createOrRefreshTask(accountHolderId, taskType, payload, null, true, true);
        if (task == null) {
            throw new NotFoundException("Active task definition not found for " + taskType.name());
        }
        if (existingTask == null) {
            emitTaskCreatedNotification(task);
        }
        return toResponse(task);
    }

    @Transactional
    public TaskResponse completeTask(UUID taskId) {
        UserTask task = userTaskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
        completeInternal(task, "MANUAL_COMPLETION");
        synchronizeTaskGraph(task.getAccountHolderId(), true);
        return toResponse(task);
    }

    @Transactional
    public TaskResponse completeTaskByType(UUID accountHolderId, TaskType taskType) {
        UserTask activeTask = findActiveTask(accountHolderId, taskType);
        if (activeTask == null) {
            activeTask = taskType.legacy()
                    ? userTaskRepository.save(buildLegacyTask(accountHolderId, taskType))
                    : createOrRefreshTask(accountHolderId, taskType, objectMapper.createObjectNode(), null, true, true);
        }
        if (activeTask == null) {
            throw new NotFoundException("Task not found");
        }
        completeInternal(activeTask, "LEGACY_COMPLETION");
        synchronizeTaskGraph(accountHolderId, true);
        return toResponse(activeTask);
    }

    @Transactional
    public TaskResponse completeActiveTaskByType(UUID accountHolderId, TaskType taskType, boolean notifyOnFollowUp) {
        UserTask activeTask = findActiveTask(accountHolderId, taskType);
        if (activeTask == null) {
            return null;
        }
        completeInternal(activeTask, "EVENT_COMPLETION");
        synchronizeTaskGraph(accountHolderId, notifyOnFollowUp);
        return toResponse(activeTask);
    }

    @Transactional
    public List<TaskResponse> getUserTasks(UUID accountHolderId) {
        synchronizeTaskGraph(accountHolderId, false);
        return userTaskRepository.findRankedByAccountHolderIdAndStatuses(accountHolderId, TaskStatus.activeStatuses()).stream()
                .limit(3)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TaskResponse getNextBestAction(UUID accountHolderId) {
        return getUserTasks(accountHolderId).stream().findFirst().orElse(null);
    }

    @Transactional
    public List<TaskResponse> getAllUserTasks(UUID accountHolderId) {
        synchronizeTaskGraph(accountHolderId, false);
        return userTaskRepository.findByAccountHolderIdOrderByCreatedAtAsc(accountHolderId).stream()
                .sorted(Comparator.comparing((UserTask task) -> !task.getStatus().active())
                        .thenComparing(UserTask::getCreatedAt))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void synchronizeTaskGraph(UUID accountHolderId, boolean notifyOnNewTasks) {
        expireOverdueTasks(accountHolderId);

        TaskStateResolver.TaskUserState state = taskStateResolver.resolve(accountHolderId);
        List<UserTask> newTasks = new ArrayList<>();

        syncOneOffTask(state, TaskType.COMPLETE_KYC, !state.kycCompleted(),
                objectNode(Map.of("kycStatus", state.accountHolder().getKycStatus().name())), null, newTasks);
        syncOneOffTask(state, TaskType.VERIFY_PHONE,
                !state.phoneVerified() && hasText(state.accountHolder().getPhoneNumber()),
                objectNode(Map.of("phoneNumber", nullable(state.accountHolder().getPhoneNumber()))), null, newTasks);
        syncOneOffTask(state, TaskType.COMPLETE_PROFILE, !state.profileComplete(),
                objectNode(Map.of(
                        "countryCode", nullable(state.accountHolder().getCountryCode()),
                        "serviceCountryCode", nullable(state.accountHolder().getServiceCountryCode())
                )), null, newTasks);
        syncOneOffTask(state, TaskType.FUND_WALLET,
                state.walletActivated() && !state.walletFunded(),
                objectNode(Map.of("accountHolderId", state.accountHolder().getId().toString())), null, newTasks);
        syncOneOffTask(state, TaskType.JOIN_CIRCLE, state.kycCompleted() && !state.joinedCircle(),
                objectNode(Map.of("countryCode", nullable(state.accountHolder().getServiceCountryCode()))), null, newTasks);
        syncOneOffTask(state, TaskType.MAKE_FIRST_CONTRIBUTION, state.joinedCircle() && !state.madeFirstContribution(),
                objectNode(Map.of("circleId", nullableUuid(state.activeCircleId()))), null, newTasks);
        syncOneOffTask(state, TaskType.INVITE_FRIENDS, state.joinedCircle() && !state.invitedMember(),
                objectNode(Map.of("circleId", nullableUuid(state.activeCircleId()))), null, newTasks);
        syncOneOffTask(state, TaskType.APPLY_LOAN,
                state.joinedCircle() && state.financingEnabled() && !state.appliedLoan(),
                objectNode(Map.of("circleId", nullableUuid(state.activeCircleId()))), null, newTasks);
        syncRepaymentTask(state, newTasks);

        if (notifyOnNewTasks) {
            newTasks.stream()
                    .max(Comparator.comparingInt(UserTask::getPriorityScore))
                    .ifPresent(this::emitTaskCreatedNotification);
        }
    }

    private void syncOneOffTask(TaskStateResolver.TaskUserState state,
                                TaskType taskType,
                                boolean shouldBeActive,
                                ObjectNode payload,
                                OffsetDateTime dueDate,
                                List<UserTask> newTasks) {
        UserTask activeTask = findActiveTask(state.accountHolder().getId(), taskType);
        boolean alreadyCompleted = userTaskRepository.existsByAccountHolderIdAndTaskTypeAndStatus(
                state.accountHolder().getId(),
                taskType,
                TaskStatus.COMPLETED
        );

        if (!shouldBeActive) {
            if (activeTask != null) {
                completeInternal(activeTask, "STATE_SYNC_COMPLETION");
            }
            return;
        }

        if (alreadyCompleted) {
            return;
        }

        UserTask task = createOrRefreshTask(state.accountHolder().getId(), taskType, payload, dueDate, false, true);
        if (task != null && activeTask == null) {
            newTasks.add(task);
        }
    }

    private void syncRepaymentTask(TaskStateResolver.TaskUserState state, List<UserTask> newTasks) {
        UserTask activeTask = findActiveTask(state.accountHolder().getId(), TaskType.REPAY_INSTALLMENT);
        TaskStateResolver.RepaymentSnapshot repayment = state.repaymentSnapshot();

        if (repayment == null) {
            if (activeTask != null) {
                completeInternal(activeTask, "NO_OPEN_INSTALLMENT");
            }
            return;
        }

        UserTask task = createOrRefreshTask(
                state.accountHolder().getId(),
                TaskType.REPAY_INSTALLMENT,
                objectNode(Map.of(
                        "loanId", repayment.loanId().toString(),
                        "installmentNumber", Integer.toString(repayment.installmentNumber()),
                        "overdue", Boolean.toString(repayment.overdue())
                )),
                repayment.dueAt(),
                false,
                true
        );
        if (task != null && activeTask == null) {
            newTasks.add(task);
        }
    }

    private UserTask createOrRefreshTask(UUID accountHolderId,
                                         TaskType taskType,
                                         JsonNode payload,
                                         OffsetDateTime dueDate,
                                         boolean strictDefinitionLookup,
                                         boolean recordAudit) {
        TaskDefinition definition = taskDefinitionRepository.findByTaskTypeAndActiveTrue(taskType).orElse(null);
        if (definition == null) {
            if (strictDefinitionLookup) {
                throw new NotFoundException("Active task definition not found for " + taskType.name());
            }
            return null;
        }

        JsonNode payloadNode = payload == null || payload.isNull() ? objectMapper.createObjectNode() : payload.deepCopy();
        Map<String, String> variables = buildTemplateVariables(accountHolderId, payloadNode);

        UserTask task = findActiveTask(accountHolderId, taskType);
        boolean isNew = task == null;
        if (task == null) {
            task = UserTask.builder()
                    .accountHolderId(accountHolderId)
                    .taskType(taskType)
                    .status(TaskStatus.PENDING)
                    .build();
        }

        task.setTitle(templateRenderer.render(definition.getTitleTemplate(), variables));
        task.setDescription(templateRenderer.render(definition.getDescriptionTemplate(), variables));
        task.setPriorityScore(resolvePriority(taskType, payloadNode, dueDate, readObject(definition.getMetadataJson())));
        task.setDueDate(dueDate);
        task.setPayloadJson(writeJson(payloadNode));
        task.setActionJson(renderAction(definition.getActionJson(), variables));

        UserTask saved = saveTaskIdempotently(accountHolderId, taskType, task, isNew);
        if (recordAudit) {
            taskAuditService.record(saved, isNew ? "TASK_CREATED" : "TASK_REFRESHED", Map.of(
                    "priorityScore", Integer.toString(saved.getPriorityScore()),
                    "dueDate", saved.getDueDate() == null ? "" : saved.getDueDate().toString(),
                    "status", saved.getStatus().name()
            ));
        }
        return saved;
    }

    private UserTask saveTaskIdempotently(UUID accountHolderId, TaskType taskType, UserTask task, boolean isNew) {
        try {
            return userTaskRepository.saveAndFlush(task);
        } catch (DataIntegrityViolationException ex) {
            UserTask concurrentTask = findActiveTask(accountHolderId, taskType);
            if (concurrentTask == null) {
                throw ex;
            }
            if (isNew) {
                concurrentTask.setTitle(task.getTitle());
                concurrentTask.setDescription(task.getDescription());
                concurrentTask.setPriorityScore(task.getPriorityScore());
                concurrentTask.setDueDate(task.getDueDate());
                concurrentTask.setPayloadJson(task.getPayloadJson());
                concurrentTask.setActionJson(task.getActionJson());
                return userTaskRepository.save(concurrentTask);
            }
            return concurrentTask;
        }
    }

    private void completeInternal(UserTask task, String reason) {
        if (task == null || task.getStatus() == TaskStatus.COMPLETED) {
            return;
        }
        if (task.getStatus() == TaskStatus.EXPIRED) {
            throw new BusinessException("Task is expired");
        }
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(OffsetDateTime.now());
        UserTask saved = userTaskRepository.save(task);
        taskAuditService.record(saved, "TASK_COMPLETED", Map.of("reason", reason));
        emitCompletionNotification(saved);
    }

    private void expireOverdueTasks(UUID accountHolderId) {
        for (UserTask task : userTaskRepository.findOverdueTasks(accountHolderId, TaskStatus.activeStatuses(), OffsetDateTime.now())) {
            if (!shouldExpire(task)) {
                continue;
            }
            task.setStatus(TaskStatus.EXPIRED);
            userTaskRepository.save(task);
            taskAuditService.record(task, "TASK_EXPIRED", Map.of(
                    "dueDate", task.getDueDate() == null ? "" : task.getDueDate().toString()
            ));
        }
    }

    private boolean shouldExpire(UserTask task) {
        if (task == null || task.getDueDate() == null || task.getTaskType() == TaskType.REPAY_INSTALLMENT) {
            return false;
        }
        ObjectNode metadata = taskDefinitionRepository.findByTaskTypeAndActiveTrue(task.getTaskType())
                .map(TaskDefinition::getMetadataJson)
                .map(this::readObject)
                .orElse(objectMapper.createObjectNode());
        return metadata.path("expireOnOverdue").asBoolean(false);
    }

    private int resolvePriority(TaskType taskType, JsonNode payload, OffsetDateTime dueDate, ObjectNode metadata) {
        int priority = metadata.path("basePriority").isInt()
                ? metadata.get("basePriority").intValue()
                : taskType.defaultPriority();

        if (taskType.financial()) {
            priority += 50;
        }
        if (taskType == TaskType.REPAY_INSTALLMENT) {
            priority += 150;
        }

        if (dueDate != null) {
            LocalDate today = OffsetDateTime.now(ZoneOffset.UTC).toLocalDate();
            LocalDate dueLocalDate = dueDate.toLocalDate();
            if (dueLocalDate.isBefore(today) || payload.path("overdue").asBoolean(false)) {
                priority += 1_000;
            } else if (dueLocalDate.isEqual(today)) {
                priority += 250;
            } else {
                priority += (int) Math.max(0, 30 - ChronoUnit.DAYS.between(today, dueLocalDate));
            }
        }

        return priority;
    }

    private Map<String, String> buildTemplateVariables(UUID accountHolderId, JsonNode payload) {
        TaskStateResolver.TaskUserState state = taskStateResolver.resolve(accountHolderId);
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("accountHolderId", accountHolderId.toString());
        variables.put("firstName", nullable(state.accountHolder().getFirstName()));
        variables.put("lastName", nullable(state.accountHolder().getLastName()));
        variables.put("fullName", (nullable(state.accountHolder().getFirstName()) + " " + nullable(state.accountHolder().getLastName())).trim());
        variables.put("countryCode", nullable(state.accountHolder().getCountryCode()));
        variables.put("serviceCountryCode", nullable(state.accountHolder().getServiceCountryCode()));
        if (payload != null && payload.isObject()) {
            payload.fields().forEachRemaining(entry -> {
                if (entry.getValue() == null || entry.getValue().isNull()) {
                    variables.put(entry.getKey(), "");
                } else if (entry.getValue().isValueNode()) {
                    variables.put(entry.getKey(), entry.getValue().asText());
                }
            });
        }
        return variables;
    }

    private String renderAction(String template, Map<String, String> variables) {
        String rendered = templateRenderer.render(template, variables);
        try {
            return objectMapper.writeValueAsString(objectMapper.readTree(rendered));
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Invalid task action configuration");
        }
    }

    private ObjectNode readObject(String rawJson) {
        try {
            JsonNode node = objectMapper.readTree(rawJson == null || rawJson.isBlank() ? "{}" : rawJson);
            return node != null && node.isObject() ? (ObjectNode) node : objectMapper.createObjectNode();
        } catch (JsonProcessingException ex) {
            return objectMapper.createObjectNode();
        }
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node == null || node.isNull() ? objectMapper.createObjectNode() : node);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private TaskResponse toResponse(UserTask task) {
        return new TaskResponse(
                task.getId(),
                task.getTaskType(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriorityScore(),
                task.getDueDate(),
                readActionNode(task.getActionJson())
        );
    }

    private JsonNode readActionNode(String rawJson) {
        try {
            return objectMapper.readTree(rawJson == null || rawJson.isBlank() ? "{}" : rawJson);
        } catch (JsonProcessingException ex) {
            return objectMapper.createObjectNode();
        }
    }

    private void emitTaskCreatedNotification(UserTask task) {
        try {
            notificationService.sendMultiChannel(new SendMultiChannelNotificationRequest(
                    task.getAccountHolderId(),
                    null,
                    NotificationType.TASK_AVAILABLE,
                    List.of(NotificationChannel.IN_APP, NotificationChannel.PUSH),
                    null,
                    null,
                    task.getTitle(),
                    Map.of(
                            "taskType", task.getTaskType().name(),
                            "taskId", task.getId() == null ? "" : task.getId().toString()
                    ),
                    true
            ));
            taskAuditService.record(task, "TASK_NOTIFICATION_SENT", Map.of("channelCount", "2"));
        } catch (RuntimeException ex) {
            log.warn("Failed to send task creation notification taskId={} accountHolderId={}", task.getId(), task.getAccountHolderId(), ex);
            taskAuditService.record(task, "TASK_NOTIFICATION_FAILED", Map.of("message", ex.getMessage() == null ? "" : ex.getMessage()));
        }
    }

    private void emitCompletionNotification(UserTask task) {
        try {
            notificationService.createNotification(new CreateNotificationRequest(
                    task.getAccountHolderId(),
                    null,
                    NotificationType.TASK_COMPLETED,
                    NotificationChannel.IN_APP,
                    null,
                    null,
                    "Tache accomplie: " + task.getTitle(),
                    Map.of("taskType", task.getTaskType().name()),
                    true
            ));
        } catch (RuntimeException ex) {
            log.warn("Failed to send task completion notification taskId={}", task.getId(), ex);
        }
    }

    private UserTask findActiveTask(UUID accountHolderId, TaskType taskType) {
        return userTaskRepository
                .findTopByAccountHolderIdAndTaskTypeAndStatusInOrderByCreatedAtDesc(accountHolderId, taskType, TaskStatus.activeStatuses())
                .orElse(null);
    }

    private UserTask buildLegacyTask(UUID accountHolderId, TaskType taskType) {
        OffsetDateTime now = OffsetDateTime.now();
        return UserTask.builder()
                .accountHolderId(accountHolderId)
                .taskType(taskType)
                .title(taskType.name())
                .description(taskType.name())
                .status(TaskStatus.PENDING)
                .priorityScore(taskType.defaultPriority())
                .createdAt(now)
                .payloadJson("{}")
                .actionJson("{}")
                .build();
    }

    private ObjectNode objectNode(Map<String, String> values) {
        ObjectNode node = objectMapper.createObjectNode();
        values.forEach((key, value) -> {
            if (value == null) {
                node.putNull(key);
            } else {
                node.put(key, value);
            }
        });
        return node;
    }

    private String nullable(String value) {
        return value == null ? "" : value;
    }

    private String nullableUuid(UUID value) {
        return value == null ? "" : value.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
