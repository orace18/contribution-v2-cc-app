package com.ccapp.mlm.service;

import com.ccapp.mlm.domain.entity.MlmPlacementAuditLog;
import com.ccapp.mlm.repository.MlmPlacementAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MlmPlacementAuditService {

    private final MlmPlacementAuditLogRepository mlmPlacementAuditLogRepository;

    @Transactional
    public void record(UUID accountHolderId,
                       UUID sponsorAccountHolderId,
                       UUID placementParentId,
                       UUID actorUserId,
                       Map<String, ?> details) {
        mlmPlacementAuditLogRepository.save(MlmPlacementAuditLog.builder()
                .accountHolderId(accountHolderId)
                .sponsorAccountHolderId(sponsorAccountHolderId)
                .placementParentId(placementParentId)
                .actorUserId(actorUserId)
                .detailsJson(toJson(details))
                .build());
    }

    private String toJson(Map<String, ?> details) {
        if (details == null || details.isEmpty()) {
            return "{}";
        }
        return details.entrySet().stream()
                .map(e -> "\"" + escape(e.getKey()) + "\":\"" + escape(String.valueOf(e.getValue())) + "\"")
                .collect(Collectors.joining(",", "{", "}"));
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
