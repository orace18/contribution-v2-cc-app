package com.ccapp.admin.controller;

import com.ccapp.admin.audit.AdminAuditService;
import com.ccapp.admin.security.AdminSecurityUtils;
import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.platform.config.domain.enums.PlatformConfigType;
import com.ccapp.platform.config.dto.PlatformConfigSnapshotResponse;
import com.ccapp.platform.config.dto.PlatformConfigVersionResponse;
import com.ccapp.platform.config.dto.UpsertPlatformConfigRequest;
import com.ccapp.platform.config.service.PlatformConfigurationService;
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
import java.util.UUID;

@RestController
@RequestMapping({"/admin/platform-config", "/api/admin/platform-config"})
@RequiredArgsConstructor
@Tag(name = "Admin Platform Config", description = "Versioned global platform configuration")
@SecurityRequirement(name = "bearerAuth")
public class AdminPlatformConfigurationController {

    private final PlatformConfigurationService platformConfigurationService;
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

    @PutMapping("/{configType}")
    public ResponseEntity<CommonResponse<PlatformConfigVersionResponse>> upsertPlatformConfig(
            @PathVariable String configType,
            @Valid @RequestBody UpsertPlatformConfigRequest request) {
        PlatformConfigType parsedConfigType = parseConfigType(configType);
        UUID adminUserId = AdminSecurityUtils.currentAdminUserId();
        PlatformConfigVersionResponse response = platformConfigurationService
                .upsertPlatformConfiguration(parsedConfigType, request, adminUserId);
        adminAuditService.logAction(
                adminUserId,
                "UPSERT_PLATFORM_CONFIG",
                "PLATFORM_CONFIG",
                response.id().toString(),
                "type=" + response.configType().externalName() + ",version=" + response.version()
        );
        return ResponseEntity.ok(CommonResponse.success("Platform configuration updated", response));
    }

    private PlatformConfigType parseConfigType(String rawConfigType) {
        try {
            return PlatformConfigType.fromExternalName(rawConfigType);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ex.getMessage());
        }
    }
}
