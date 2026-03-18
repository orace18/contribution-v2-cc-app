package com.ccapp.admin.controller;

import com.ccapp.admin.audit.AdminAuditService;
import com.ccapp.admin.service.AdminConfigMutationSecurityService;
import com.ccapp.admin.security.AdminSecurityUtils;
import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.dto.ActivateCountryConfigVersionRequest;
import com.ccapp.country.config.dto.CountryChartAccountDetailsResponse;
import com.ccapp.country.config.dto.CountryChartAccountHistoryResponse;
import com.ccapp.country.config.dto.CountryChartAccountSummaryResponse;
import com.ccapp.country.config.dto.CountryConfigFullResponse;
import com.ccapp.country.config.dto.CountryConfigReadinessResponse;
import com.ccapp.country.config.dto.CountryConfigSnapshotResponse;
import com.ccapp.country.config.dto.CountrySetupCountryResponse;
import com.ccapp.country.config.dto.CountryConfigVersionResponse;
import com.ccapp.country.config.dto.CountryConfigVersionHistoryItemResponse;
import com.ccapp.country.config.dto.SecureActivateCountryConfigVersionRequest;
import com.ccapp.country.config.dto.SecureUpsertCountryConfigRequest;
import com.ccapp.country.config.dto.UpsertCountryConfigRequest;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.country.config.service.CountryConfigReadinessService;
import com.ccapp.country.config.service.CountryConfigurationEngineService;
import com.ccapp.country.config.service.CountrySetupBootstrapService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/admin/country-config", "/api/admin/country-config"})
@RequiredArgsConstructor
@Tag(name = "Admin Country Config", description = "Versioned country configuration engine")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
public class AdminCountryConfigurationEngineController {

    private final CountryConfigurationEngineService countryConfigurationEngineService;
    private final CountryConfigReadinessService countryConfigReadinessService;
    private final CountryChartOfAccountsService countryChartOfAccountsService;
    private final CountrySetupBootstrapService countrySetupBootstrapService;
    private final AdminConfigMutationSecurityService adminConfigMutationSecurityService;
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

    @GetMapping("/configured-countries")
    public ResponseEntity<CommonResponse<List<CountrySetupCountryResponse>>> listConfiguredCountries() {
        List<CountrySetupCountryResponse> response = countrySetupBootstrapService.listConfiguredCountries();
        adminAuditService.logAction(
                AdminSecurityUtils.currentAdminUserId(),
                "LIST_CONFIGURED_COUNTRIES",
                "COUNTRY_CONFIG",
                null,
                "count=" + response.size()
        );
        return ResponseEntity.ok(CommonResponse.success("Configured countries", response));
    }

    @GetMapping("/unconfigured-countries")
    public ResponseEntity<CommonResponse<List<CountrySetupCountryResponse>>> listUnconfiguredCountries() {
        List<CountrySetupCountryResponse> response = countrySetupBootstrapService.listUnconfiguredCountries();
        adminAuditService.logAction(
                AdminSecurityUtils.currentAdminUserId(),
                "LIST_UNCONFIGURED_COUNTRIES",
                "COUNTRY_CONFIG",
                null,
                "count=" + response.size()
        );
        return ResponseEntity.ok(CommonResponse.success("Unconfigured countries", response));
    }

    @PostMapping("/{countryCode}/initialize")
    public ResponseEntity<CommonResponse<CountryConfigSnapshotResponse>> initializeCountryConfig(@PathVariable String countryCode) {
        UUID adminUserId = AdminSecurityUtils.currentAdminUserId();
        String normalizedCountryCode = countryCode == null ? "" : countryCode.trim().toUpperCase();
        log.info("Country configuration initialize attempt: countryCode={}, adminUserId={}", normalizedCountryCode, adminUserId);

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
            @Valid @RequestBody SecureUpsertCountryConfigRequest request) {
        CountryConfigType parsedConfigType = parseConfigType(configType);
        UUID adminUserId = AdminSecurityUtils.currentAdminUserId();
        AdminConfigMutationSecurityService.ConfigMutationApprovalContext approvalContext =
                adminConfigMutationSecurityService.validateMutationApproval(adminUserId, request.approval());
        OffsetDateTime asOf = request.effectiveDate() == null ? OffsetDateTime.now() : request.effectiveDate();
        CountryConfigSnapshotResponse beforeSnapshot = countryConfigurationEngineService.getCountryConfiguration(countryCode, asOf);
        JsonNode beforePayload = payloadOf(beforeSnapshot, parsedConfigType);

        CountryConfigVersionResponse response = countryConfigurationEngineService.upsertCountryConfiguration(
                countryCode,
                parsedConfigType,
                new UpsertCountryConfigRequest(request.payload(), request.effectiveDate()),
                adminUserId
        );
        adminAuditService.logAction(
                adminUserId,
                "UPSERT_COUNTRY_CONFIG",
                "COUNTRY_CONFIG",
                response.id().toString(),
                buildConfigMutationAuditPayload(
                        response.countryCode(),
                        parsedConfigType.externalName(),
                        "UPSERT",
                        beforePayload,
                        response.payload(),
                        response.effectiveDate(),
                        response.version(),
                        null,
                        approvalContext
                )
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
            @Valid @RequestBody SecureActivateCountryConfigVersionRequest request) {
        CountryConfigType parsedConfigType = parseConfigType(configType);
        UUID adminUserId = AdminSecurityUtils.currentAdminUserId();
        AdminConfigMutationSecurityService.ConfigMutationApprovalContext approvalContext =
                adminConfigMutationSecurityService.validateMutationApproval(adminUserId, request.approval());
        OffsetDateTime asOf = request.effectiveDate() == null ? OffsetDateTime.now() : request.effectiveDate();
        CountryConfigSnapshotResponse beforeSnapshot = countryConfigurationEngineService.getCountryConfiguration(countryCode, asOf);
        JsonNode beforePayload = payloadOf(beforeSnapshot, parsedConfigType);

        CountryConfigVersionResponse response = countryConfigurationEngineService.activateCountryConfigurationVersion(
                countryCode,
                parsedConfigType,
                new ActivateCountryConfigVersionRequest(request.version(), request.effectiveDate()),
                adminUserId
        );
        adminAuditService.logAction(
                adminUserId,
                "ACTIVATE_COUNTRY_CONFIG_VERSION",
                "COUNTRY_CONFIG",
                response.id().toString(),
                buildConfigMutationAuditPayload(
                        response.countryCode(),
                        parsedConfigType.externalName(),
                        "ACTIVATE",
                        beforePayload,
                        response.payload(),
                        response.effectiveDate(),
                        response.version(),
                        request.version(),
                        approvalContext
                )
        );
        return ResponseEntity.ok(CommonResponse.success("Country configuration version activated", response));
    }

    @GetMapping("/{countryCode}/readiness")
    public ResponseEntity<CommonResponse<CountryConfigReadinessResponse>> getCountryReadiness(@PathVariable String countryCode) {
        CountryConfigReadinessResponse response = countryConfigReadinessService.refreshCountryConfiguredStatus(countryCode);
        adminAuditService.logAction(
                AdminSecurityUtils.currentAdminUserId(),
                "GET_COUNTRY_CONFIG_READINESS",
                "COUNTRY_CONFIG",
                countryCode,
                "configured=" + response.configured() + ",score=" + response.score()
        );
        return ResponseEntity.ok(CommonResponse.success("Country readiness", response));
    }

    @GetMapping("/{countryCode}/accounts")
    public ResponseEntity<CommonResponse<List<CountryChartAccountSummaryResponse>>> listCountryAccounts(@PathVariable String countryCode) {
        List<CountryChartAccountSummaryResponse> response = countryChartOfAccountsService.listCountryAccounts(countryCode);
        adminAuditService.logAction(
                AdminSecurityUtils.currentAdminUserId(),
                "LIST_COUNTRY_CHART_ACCOUNTS",
                "COUNTRY_ACCOUNT",
                countryCode,
                "count=" + response.size()
        );
        return ResponseEntity.ok(CommonResponse.success("Country chart of accounts", response));
    }

    @GetMapping("/{countryCode}/accounts/{accountId}")
    public ResponseEntity<CommonResponse<CountryChartAccountDetailsResponse>> getCountryAccount(
            @PathVariable String countryCode,
            @PathVariable UUID accountId) {
        CountryChartAccountDetailsResponse response = countryChartOfAccountsService.getCountryAccount(countryCode, accountId);
        adminAuditService.logAction(
                AdminSecurityUtils.currentAdminUserId(),
                "GET_COUNTRY_CHART_ACCOUNT",
                "COUNTRY_ACCOUNT",
                accountId.toString(),
                "country=" + countryCode
        );
        return ResponseEntity.ok(CommonResponse.success("Country account", response));
    }

    @GetMapping("/{countryCode}/accounts/{accountId}/history")
    public ResponseEntity<CommonResponse<CountryChartAccountHistoryResponse>> getCountryAccountHistory(
            @PathVariable String countryCode,
            @PathVariable UUID accountId) {
        CountryChartAccountHistoryResponse response = countryChartOfAccountsService.getCountryAccountHistory(countryCode, accountId);
        adminAuditService.logAction(
                AdminSecurityUtils.currentAdminUserId(),
                "GET_COUNTRY_CHART_ACCOUNT_HISTORY",
                "COUNTRY_ACCOUNT",
                accountId.toString(),
                "country=" + countryCode + ",movements=" + response.movements().size()
        );
        return ResponseEntity.ok(CommonResponse.success("Country account history", response));
    }

    private JsonNode payloadOf(CountryConfigSnapshotResponse snapshot, CountryConfigType configType) {
        return snapshot.configs().stream()
                .filter(item -> item.configType() == configType)
                .map(item -> item.payload())
                .filter(payload -> payload != null && !payload.isNull())
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> buildConfigMutationAuditPayload(String countryCode,
                                                                String configType,
                                                                String mutationType,
                                                                JsonNode beforeValue,
                                                                JsonNode afterValue,
                                                                OffsetDateTime effectiveDate,
                                                                Integer targetVersion,
                                                                Integer sourceVersion,
                                                                AdminConfigMutationSecurityService.ConfigMutationApprovalContext approvalContext) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("countryCode", countryCode);
        payload.put("configType", configType);
        payload.put("mutationType", mutationType);
        payload.put("before", beforeValue);
        payload.put("after", afterValue);
        payload.put("changedBy", approvalContext.changedByAdminUserId());
        payload.put("approvedBy", approvalContext.approvedByAdminUserId());
        payload.put("effectiveDate", effectiveDate);
        payload.put("targetVersion", targetVersion);
        payload.put("sourceVersion", sourceVersion);
        payload.put("reasonForChange", approvalContext.reasonForChange());
        payload.put("timestamp", OffsetDateTime.now());
        return payload;
    }

    private CountryConfigType parseConfigType(String rawConfigType) {
        try {
            return CountryConfigType.fromExternalName(rawConfigType);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ex.getMessage());
        }
    }
}
