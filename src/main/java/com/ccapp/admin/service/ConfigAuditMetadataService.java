package com.ccapp.admin.service;

import com.ccapp.admin.audit.AdminAuditLog;
import com.ccapp.admin.repository.AdminAuditLogRepository;
import com.ccapp.common.dto.ConfigAuditMetadataResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConfigAuditMetadataService {

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ConfigAuditMetadataResponse build(String entityType,
                                             UUID entityId,
                                             Integer version,
                                             String status,
                                             OffsetDateTime effectiveDate,
                                             OffsetDateTime createdAt,
                                             UUID createdBy) {
        if (entityId == null) {
            return new ConfigAuditMetadataResponse(
                    version,
                    status,
                    effectiveDate,
                    createdAt,
                    createdBy,
                    createdAt,
                    createdBy,
                    null,
                    null
            );
        }

        AdminAuditLog latestAudit = adminAuditLogRepository
                .findFirstByEntityTypeAndEntityIdOrderByTimestampDesc(entityType, entityId.toString())
                .orElse(null);

        JsonNode payload = parsePayload(latestAudit == null ? null : latestAudit.getPayload());
        UUID changedBy = readUuid(payload, "changedBy");
        UUID approvedBy = readUuid(payload, "approvedBy");
        String reasonForChange = readText(payload, "reasonForChange");

        OffsetDateTime lastModifiedAt = latestAudit == null ? createdAt : latestAudit.getTimestamp();
        UUID lastModifiedBy = changedBy != null
                ? changedBy
                : (latestAudit == null ? createdBy : latestAudit.getAdminUserId());
        UUID resolvedCreatedBy = createdBy != null ? createdBy : lastModifiedBy;
        OffsetDateTime resolvedCreatedAt = createdAt != null ? createdAt : lastModifiedAt;

        return new ConfigAuditMetadataResponse(
                version,
                status,
                effectiveDate,
                resolvedCreatedAt,
                resolvedCreatedBy,
                lastModifiedAt,
                lastModifiedBy,
                approvedBy,
                reasonForChange
        );
    }

    private JsonNode parsePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            JsonNode parsed = objectMapper.readTree(payload);
            return parsed.isObject() ? parsed : null;
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private UUID readUuid(JsonNode payload, String fieldName) {
        if (payload == null || !payload.has(fieldName)) {
            return null;
        }
        JsonNode node = payload.get(fieldName);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual() || node.asText().isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(node.asText().trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String readText(JsonNode payload, String fieldName) {
        if (payload == null || !payload.has(fieldName)) {
            return null;
        }
        JsonNode node = payload.get(fieldName);
        if (node == null || !node.isTextual()) {
            return null;
        }
        String value = node.asText().trim();
        return value.isEmpty() ? null : value;
    }
}
