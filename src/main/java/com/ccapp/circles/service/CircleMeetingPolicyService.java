package com.ccapp.circles.service;

import com.ccapp.circles.domain.entity.Circle;
import com.ccapp.circles.domain.enums.CircleMeetingFrequency;
import com.ccapp.circles.dto.CircleMeetingPolicyResponse;
import com.ccapp.circles.dto.UpdateCircleMeetingPolicyRequest;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.seedcapital.readiness.service.CircleReadinessAccessService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CircleMeetingPolicyService {

    private static final CircleMeetingFrequency DEFAULT_FREQUENCY = CircleMeetingFrequency.WEEKLY;
    private static final String MEETING_POLICY_NODE = "meetingPolicy";

    private final CircleRepository circleRepository;
    private final CircleReadinessAccessService circleReadinessAccessService;
    private final ObjectMapper objectMapper;

    @Transactional
    public CircleMeetingPolicyResponse updatePolicy(UUID requesterAccountHolderId,
                                                    UUID circleId,
                                                    UpdateCircleMeetingPolicyRequest request) {
        circleReadinessAccessService.assertCanManageMeetings(requesterAccountHolderId, circleId);
        Circle circle = circleReadinessAccessService.requireActiveCircle(circleId);
        OffsetDateTime now = OffsetDateTime.now();

        ObjectNode snapshot = readSnapshot(circle);
        ObjectNode meetingPolicy = snapshot.with(MEETING_POLICY_NODE);
        meetingPolicy.put("frequency", request.frequency().name());
        meetingPolicy.put("updatedAt", now.toString());
        circle.setConfigSnapshot(writeSnapshot(snapshot));
        circleRepository.save(circle);

        return new CircleMeetingPolicyResponse(circle.getId(), request.frequency(), now);
    }

    @Transactional(readOnly = true)
    public CircleMeetingPolicyResponse resolvePolicy(Circle circle) {
        CircleMeetingFrequency frequency = DEFAULT_FREQUENCY;
        OffsetDateTime updatedAt = circle.getUpdatedAt() != null ? circle.getUpdatedAt() : circle.getCreatedAt();
        JsonNode meetingPolicy = readSnapshot(circle).path(MEETING_POLICY_NODE);
        if (meetingPolicy.isObject()) {
            JsonNode frequencyNode = meetingPolicy.get("frequency");
            if (frequencyNode != null && frequencyNode.isTextual()) {
                try {
                    frequency = CircleMeetingFrequency.valueOf(frequencyNode.asText().trim().toUpperCase());
                } catch (IllegalArgumentException ignored) {
                    frequency = DEFAULT_FREQUENCY;
                }
            }
            JsonNode updatedAtNode = meetingPolicy.get("updatedAt");
            if (updatedAtNode != null && updatedAtNode.isTextual()) {
                try {
                    updatedAt = OffsetDateTime.parse(updatedAtNode.asText().trim());
                } catch (RuntimeException ignored) {
                    updatedAt = circle.getUpdatedAt() != null ? circle.getUpdatedAt() : circle.getCreatedAt();
                }
            }
        }
        return new CircleMeetingPolicyResponse(circle.getId(), frequency, updatedAt);
    }

    private ObjectNode readSnapshot(Circle circle) {
        if (circle.getConfigSnapshot() == null || circle.getConfigSnapshot().isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(circle.getConfigSnapshot());
            return node != null && node.isObject()
                    ? (ObjectNode) node
                    : objectMapper.createObjectNode();
        } catch (JsonProcessingException exception) {
            throw new BusinessException("Unable to parse circle configuration snapshot");
        }
    }

    private String writeSnapshot(ObjectNode snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("Unable to serialize circle configuration snapshot");
        }
    }
}
