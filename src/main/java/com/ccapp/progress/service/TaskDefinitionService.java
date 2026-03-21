package com.ccapp.progress.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.progress.domain.entity.TaskDefinition;
import com.ccapp.progress.dto.TaskDefinitionRequest;
import com.ccapp.progress.dto.TaskDefinitionResponse;
import com.ccapp.progress.repository.TaskDefinitionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskDefinitionService {

    private static final Set<String> SUPPORTED_ACTION_TYPES = Set.of("NAVIGATE", "FLOW", "API_CALL", "DEEPLINK");

    private final TaskDefinitionRepository taskDefinitionRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<TaskDefinitionResponse> listDefinitions() {
        return taskDefinitionRepository.findAllByOrderByTaskTypeAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskDefinitionResponse getDefinition(UUID id) {
        return toResponse(taskDefinitionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Task definition not found")));
    }

    @Transactional
    public TaskDefinitionResponse createDefinition(TaskDefinitionRequest request) {
        validateRequest(request);
        if (taskDefinitionRepository.existsByTaskType(request.taskType())) {
            throw new BusinessException("Task definition already exists for " + request.taskType().name());
        }
        TaskDefinition definition = TaskDefinition.builder().build();
        applyRequest(definition, request);
        return toResponse(taskDefinitionRepository.save(definition));
    }

    @Transactional
    public TaskDefinitionResponse updateDefinition(UUID id, TaskDefinitionRequest request) {
        validateRequest(request);
        TaskDefinition definition = taskDefinitionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Task definition not found"));
        if (taskDefinitionRepository.existsByTaskTypeAndIdNot(request.taskType(), id)) {
            throw new BusinessException("Task definition already exists for " + request.taskType().name());
        }
        applyRequest(definition, request);
        return toResponse(taskDefinitionRepository.save(definition));
    }

    @Transactional
    public TaskDefinitionResponse disableDefinition(UUID id) {
        TaskDefinition definition = taskDefinitionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Task definition not found"));
        definition.setActive(false);
        return toResponse(taskDefinitionRepository.save(definition));
    }

    private void validateRequest(TaskDefinitionRequest request) {
        if (request == null) {
            throw new BusinessException("Task definition request is required");
        }
        if (request.taskType() == null) {
            throw new BusinessException("taskType is required");
        }
        if (request.triggerEvent() == null) {
            throw new BusinessException("triggerEvent is required");
        }
    }

    private void applyRequest(TaskDefinition definition, TaskDefinitionRequest request) {
        if (request.taskType().legacy()) {
            throw new BusinessException("Legacy task types cannot be configured");
        }
        validateObjectPayload(request.actionJson(), "actionJson");
        validateActionType(request.actionJson());
        if (request.metadataJson() != null) {
            validateObjectPayload(request.metadataJson(), "metadataJson");
        }

        definition.setTaskType(request.taskType());
        definition.setTitleTemplate(request.titleTemplate().trim());
        definition.setDescriptionTemplate(trimToNull(request.descriptionTemplate()));
        definition.setTriggerEvent(request.triggerEvent());
        definition.setActionJson(writeJson(request.actionJson()));
        definition.setRewardPoints(request.rewardPoints());
        definition.setActive(request.isActive());
        definition.setMetadataJson(writeJson(request.metadataJson()));
    }

    private void validateObjectPayload(JsonNode payload, String fieldName) {
        if (payload == null || payload.isNull()) {
            throw new BusinessException(fieldName + " is required");
        }
        if (!payload.isObject()) {
            throw new BusinessException(fieldName + " must be a JSON object");
        }
    }

    private void validateActionType(JsonNode actionJson) {
        JsonNode typeNode = actionJson.get("type");
        if (typeNode == null || !typeNode.isTextual()) {
            throw new BusinessException("actionJson.type is required");
        }
        if (!SUPPORTED_ACTION_TYPES.contains(typeNode.asText().trim().toUpperCase())) {
            throw new BusinessException("Unsupported actionJson.type");
        }
    }

    private TaskDefinitionResponse toResponse(TaskDefinition definition) {
        return new TaskDefinitionResponse(
                definition.getId(),
                definition.getTaskType(),
                definition.getTitleTemplate(),
                definition.getDescriptionTemplate(),
                definition.getTriggerEvent(),
                readJson(definition.getActionJson()),
                definition.getRewardPoints(),
                definition.isActive(),
                readJson(definition.getMetadataJson()),
                definition.getCreatedAt(),
                definition.getUpdatedAt()
        );
    }

    private JsonNode readJson(String rawJson) {
        try {
            return objectMapper.readTree(rawJson == null || rawJson.isBlank() ? "{}" : rawJson);
        } catch (JsonProcessingException ex) {
            return objectMapper.createObjectNode();
        }
    }

    private String writeJson(JsonNode jsonNode) {
        try {
            return objectMapper.writeValueAsString(jsonNode == null || jsonNode.isNull()
                    ? objectMapper.createObjectNode()
                    : jsonNode);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Invalid JSON payload");
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
