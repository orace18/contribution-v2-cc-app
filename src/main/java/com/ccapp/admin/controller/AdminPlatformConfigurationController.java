package com.ccapp.admin.controller;

import com.ccapp.admin.audit.AdminAuditService;
import com.ccapp.admin.security.AdminSecurityUtils;
import com.ccapp.admin.service.AdminConfigMutationSecurityService;
import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.platform.config.dto.SecureUpsertPlatformConfigRequest;
import com.ccapp.platform.config.domain.enums.PlatformConfigType;
import com.ccapp.platform.config.dto.PlatformConfigReadinessResponse;
import com.ccapp.platform.config.dto.PlatformConfigSnapshotResponse;
import com.ccapp.platform.config.dto.PlatformConfigVersionResponse;
import com.ccapp.platform.config.dto.UpsertPlatformConfigRequest;
import com.ccapp.platform.config.service.PlatformConfigReadinessService;
import com.ccapp.platform.config.service.PlatformConfigurationService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/admin/platform-config", "/api/admin/platform-config"})
@RequiredArgsConstructor
@Tag(name = "Admin Platform Config", description = "Versioned global platform configuration")
@SecurityRequirement(name = "bearerAuth")
public class AdminPlatformConfigurationController {

    private final PlatformConfigurationService platformConfigurationService;
    private final PlatformConfigReadinessService platformConfigReadinessService;
    private final AdminConfigMutationSecurityService adminConfigMutationSecurityService;
    private final AdminAuditService adminAuditService;

    @GetMapping
    public ResponseEntity<CommonResponse<PlatformConfigSnapshotResponse>> getPlatformConfig(
            @RequestParam(required = false) OffsetDateTime asOf) {
        PlatformConfigSnapshotResponse response = platformConfigurationService.getPlatformConfiguration(asOf);
        adminAuditService.logAction(
                AdminSecurityUtils.currentAdminUserId(),
                "GET_PLATFORM_CONFIG_SNAPSHOT",
                "PLATFORM_CONFIG",
                null,
                "asOf=" + response.asOf()
        );
        return ResponseEntity.ok(CommonResponse.success("Platform configuration", response));
    }

    @GetMapping("/readiness")
    public ResponseEntity<CommonResponse<PlatformConfigReadinessResponse>> getPlatformReadiness(
            @RequestParam(required = false) OffsetDateTime asOf) {
        PlatformConfigReadinessResponse response = platformConfigReadinessService.evaluateReadiness(asOf);
        adminAuditService.logAction(
                AdminSecurityUtils.currentAdminUserId(),
                "GET_PLATFORM_CONFIG_READINESS",
                "PLATFORM_CONFIG",
                null,
                "ready=" + response.ready() + ",score=" + response.score()
        );
        return ResponseEntity.ok(CommonResponse.success("Platform readiness", response));
    }

    @PutMapping("/{configType}")
    public ResponseEntity<CommonResponse<PlatformConfigVersionResponse>> upsertPlatformConfig(
            @PathVariable String configType,
            @Valid @RequestBody SecureUpsertPlatformConfigRequest request) {
        PlatformConfigType parsedConfigType = parseConfigType(configType);
        UUID adminUserId = AdminSecurityUtils.currentAdminUserId();
        AdminConfigMutationSecurityService.ConfigMutationApprovalContext approvalContext =
                adminConfigMutationSecurityService.validateMutationApproval(adminUserId, request.approval());
        OffsetDateTime asOf = request.effectiveDate() == null ? OffsetDateTime.now() : request.effectiveDate();
        PlatformConfigSnapshotResponse beforeSnapshot = platformConfigurationService.getPlatformConfiguration(asOf);
        JsonNode beforePayload = beforeSnapshot.configs().stream()
                .filter(item -> item.configType() == parsedConfigType)
                .map(item -> item.payload())
                .filter(payload -> payload != null && !payload.isNull())
                .findFirst()
                .orElse(null);

        PlatformConfigVersionResponse response = platformConfigurationService
                .upsertPlatformConfiguration(
                        parsedConfigType,
                        new UpsertPlatformConfigRequest(request.payload(), request.effectiveDate()),
                        adminUserId
                );
        adminAuditService.logAction(
                adminUserId,
                "UPSERT_PLATFORM_CONFIG",
                "PLATFORM_CONFIG",
                response.id().toString(),
                buildConfigMutationAuditPayload(
                        parsedConfigType.externalName(),
                        beforePayload,
                        response.payload(),
                        response.effectiveDate(),
                        response.version(),
                        approvalContext
                )
        );
        return ResponseEntity.ok(CommonResponse.success("Platform configuration updated", response));
    }

    private Map<String, Object> buildConfigMutationAuditPayload(String configType,
                                                                JsonNode beforeValue,
                                                                JsonNode afterValue,
                                                                OffsetDateTime effectiveDate,
                                                                Integer targetVersion,
                                                                AdminConfigMutationSecurityService.ConfigMutationApprovalContext approvalContext) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("configType", configType);
        payload.put("mutationType", "UPSERT");
        payload.put("before", beforeValue);
        payload.put("after", afterValue);
        payload.put("changedBy", approvalContext.changedByAdminUserId());
        payload.put("approvedBy", approvalContext.approvedByAdminUserId());
        payload.put("effectiveDate", effectiveDate);
        payload.put("targetVersion", targetVersion);
        payload.put("reasonForChange", approvalContext.reasonForChange());
        payload.put("timestamp", OffsetDateTime.now());
        return payload;
    }

    private PlatformConfigType parseConfigType(String rawConfigType) {
        try {
            return PlatformConfigType.fromExternalName(rawConfigType);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ex.getMessage());
        }
    }
}
