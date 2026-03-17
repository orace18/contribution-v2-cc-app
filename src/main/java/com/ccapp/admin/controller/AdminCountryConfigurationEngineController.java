package com.ccapp.admin.controller;

import com.ccapp.admin.audit.AdminAuditService;
import com.ccapp.admin.security.AdminSecurityUtils;
import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.dto.ActivateCountryConfigVersionRequest;
import com.ccapp.country.config.dto.CountryConfigFullResponse;
import com.ccapp.country.config.dto.CountryConfigSnapshotResponse;
import com.ccapp.country.config.dto.CountryConfigVersionResponse;
import com.ccapp.country.config.dto.CountryConfigVersionHistoryItemResponse;
import com.ccapp.country.config.dto.UpsertCountryConfigRequest;
import com.ccapp.country.config.service.CountryConfigurationEngineService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/admin/country-config", "/api/admin/country-config"})
@RequiredArgsConstructor
@Tag(name = "Admin Country Config", description = "Versioned country configuration engine")
@SecurityRequirement(name = "bearerAuth")
public class AdminCountryConfigurationEngineController {

    private final CountryConfigurationEngineService countryConfigurationEngineService;
    private final AdminAuditService adminAuditService;

    @GetMapping("/{countryCode}")
    public ResponseEntity<CommonResponse<CountryConfigSnapshotResponse>> getCountryConfig(
            @PathVariable String countryCode,
            @RequestParam(required = false) OffsetDateTime asOf) {
        CountryConfigSnapshotResponse response = countryConfigurationEngineService.getCountryConfiguration(countryCode, asOf);
        adminAuditService.logAction(
                AdminSecurityUtils.currentAdminUserId(),
                "GET_COUNTRY_CONFIG_SNAPSHOT",
                "COUNTRY_CONFIG",
                response.countryCode(),
                "asOf=" + response.asOf()
        );
        return ResponseEntity.ok(CommonResponse.success("Country configuration", response));
    }

    @GetMapping("/{countryCode}/full")
    public ResponseEntity<CommonResponse<CountryConfigFullResponse>> getCountryConfigFull(
            @PathVariable String countryCode,
            @RequestParam(required = false) OffsetDateTime asOf) {
        CountryConfigFullResponse response = countryConfigurationEngineService.getFullCountryConfiguration(countryCode, asOf);
        adminAuditService.logAction(
                AdminSecurityUtils.currentAdminUserId(),
                "GET_COUNTRY_CONFIG_FULL",
                "COUNTRY_CONFIG",
                countryCode,
                "asOf=" + asOf
        );
        return ResponseEntity.ok(CommonResponse.success("Full country configuration", response));
    }

    @PostMapping("/{countryCode}/initialize")
    public ResponseEntity<CommonResponse<CountryConfigSnapshotResponse>> initializeCountryConfig(@PathVariable String countryCode) {
        UUID adminUserId = AdminSecurityUtils.currentAdminUserId();
        CountryConfigSnapshotResponse response = countryConfigurationEngineService.initializeCountryConfiguration(countryCode, adminUserId);
        adminAuditService.logAction(
                adminUserId,
                "INITIALIZE_COUNTRY_CONFIG",
                "COUNTRY_CONFIG",
                countryCode,
                "initialized=true"
        );
        return ResponseEntity.ok(CommonResponse.success("Country configuration initialized", response));
    }

    @PutMapping("/{countryCode}/{configType}")
    public ResponseEntity<CommonResponse<CountryConfigVersionResponse>> upsertCountryConfig(
            @PathVariable String countryCode,
            @PathVariable String configType,
            @Valid @RequestBody UpsertCountryConfigRequest request) {
        CountryConfigType parsedConfigType = parseConfigType(configType);
        UUID adminUserId = AdminSecurityUtils.currentAdminUserId();
        CountryConfigVersionResponse response = countryConfigurationEngineService.upsertCountryConfiguration(
                countryCode,
                parsedConfigType,
                request,
                adminUserId
        );
        adminAuditService.logAction(
                adminUserId,
                "UPSERT_COUNTRY_CONFIG",
                "COUNTRY_CONFIG",
                response.id().toString(),
                "country=" + response.countryCode() + ",type=" + response.configType().externalName() + ",version=" + response.version()
        );
        return ResponseEntity.ok(CommonResponse.success("Country configuration updated", response));
    }

    @GetMapping("/{countryCode}/{configType}/versions")
    public ResponseEntity<CommonResponse<List<CountryConfigVersionHistoryItemResponse>>> listCountryConfigVersions(
            @PathVariable String countryCode,
            @PathVariable String configType) {
        CountryConfigType parsedConfigType = parseConfigType(configType);
        List<CountryConfigVersionHistoryItemResponse> response = countryConfigurationEngineService
                .listCountryConfigurationVersions(countryCode, parsedConfigType);
        adminAuditService.logAction(
                AdminSecurityUtils.currentAdminUserId(),
                "LIST_COUNTRY_CONFIG_VERSIONS",
                "COUNTRY_CONFIG",
                countryCode,
                "type=" + parsedConfigType.externalName() + ",count=" + response.size()
        );
        return ResponseEntity.ok(CommonResponse.success("Country configuration versions", response));
    }

    @PostMapping("/{countryCode}/{configType}/activate")
    public ResponseEntity<CommonResponse<CountryConfigVersionResponse>> activateCountryConfigVersion(
            @PathVariable String countryCode,
            @PathVariable String configType,
            @Valid @RequestBody ActivateCountryConfigVersionRequest request) {
        CountryConfigType parsedConfigType = parseConfigType(configType);
        UUID adminUserId = AdminSecurityUtils.currentAdminUserId();
        CountryConfigVersionResponse response = countryConfigurationEngineService.activateCountryConfigurationVersion(
                countryCode,
                parsedConfigType,
                request,
                adminUserId
        );
        adminAuditService.logAction(
                adminUserId,
                "ACTIVATE_COUNTRY_CONFIG_VERSION",
                "COUNTRY_CONFIG",
                response.id().toString(),
                "country=" + response.countryCode()
                        + ",type=" + parsedConfigType.externalName()
                        + ",sourceVersion=" + request.version()
                        + ",activatedVersion=" + response.version()
                        + ",effectiveDate=" + response.effectiveDate()
        );
        return ResponseEntity.ok(CommonResponse.success("Country configuration version activated", response));
    }

    private CountryConfigType parseConfigType(String rawConfigType) {
        try {
            return CountryConfigType.fromExternalName(rawConfigType);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ex.getMessage());
        }
    }
}
