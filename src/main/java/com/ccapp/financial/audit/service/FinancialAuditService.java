package com.ccapp.financial.audit.service;

import com.ccapp.financial.audit.domain.entity.FinancialAuditLog;
import com.ccapp.financial.audit.repository.FinancialAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FinancialAuditService {

    private final FinancialAuditLogRepository financialAuditLogRepository;

    @Transactional
    public void record(String eventType,
                       String referenceId,
                       UUID accountHolderId,
                       BigDecimal amount,
                       Map<String, ?> metadata) {
        financialAuditLogRepository.save(FinancialAuditLog.builder()
                .eventType(eventType)
                .referenceId(referenceId)
                .accountHolderId(accountHolderId)
                .amount(amount)
                .metadataJson(toJson(metadata))
                .build());
    }

    private String toJson(Map<String, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        return metadata.entrySet().stream()
                .map(entry -> "\"" + escape(entry.getKey()) + "\":\"" + escape(String.valueOf(entry.getValue())) + "\"")
                .collect(Collectors.joining(",", "{", "}"));
    }

    private String escape(String input) {
        return input == null ? "" : input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
