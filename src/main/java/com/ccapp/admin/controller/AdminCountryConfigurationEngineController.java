package com.ccapp.admin.controller;

import com.ccapp.admin.audit.AdminAuditService;
import com.ccapp.admin.dto.AdminCountryWorkspaceDetailResponse;
import com.ccapp.admin.dto.AdminCountryWorkspaceListResponse;
import com.ccapp.admin.service.AdminConfigMutationSecurityService;
import com.ccapp.admin.service.AdminCountryWorkspaceService;
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
import com.ccapp.country.config.dto.CountryConfigValidationResponse;
import com.ccapp.country.config.dto.CountryConfigurationStatusResponse;
import com.ccapp.country.config.dto.CountrySetupCountryResponse;
import com.ccapp.country.config.dto.CountryConfigVersionResponse;
import com.ccapp.country.config.dto.CountryConfigVersionHistoryItemResponse;
import com.ccapp.country.config.dto.CountrySetupActivationPayloadsRequest;
import com.ccapp.country.config.dto.CreateCountryChartAccountRequest;
import com.ccapp.country.config.dto.SecureActivateCountrySetupRequest;
import com.ccapp.country.config.dto.SecureActivateCountryConfigVersionRequest;
import com.ccapp.country.config.dto.SecureUpsertCountryConfigRequest;
import com.ccapp.country.config.dto.UpsertCountryConfigRequest;
import com.ccapp.country.config.dto.ValidateCountrySetupRequest;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.country.config.service.CountryConfigReadinessService;
import com.ccapp.country.config.service.CountryConfigurationStatusService;
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
import java.util.ArrayList;
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
    private final CountryConfigurationStatusService countryConfigurationStatusService;
    private final AdminCountryWorkspaceService adminCountryWorkspaceService;
    private final AdminConfigMutationSecurityService adminConfigMutationSecurityService;
    private final AdminAuditService adminAuditService;

    @GetMapping("/workspace/countries")
    public ResponseEntity<CommonResponse<AdminCountryWorkspaceListResponse>> getCountriesWorkspace() {
        AdminCountryWorkspaceListResponse response = adminCountryWorkspaceService.getCountriesOverview();
        adminAuditService.logAction(
                AdminSecurityUtils.currentAdminUserId(),
                "LIST_COUNTRY_WORKSPACE_COUNTRIES",
                "COUNTRY_CONFIG_WORKSPACE",
                null,
                Map.of(
                        "configuredCount", response.configuredCountries().size(),
                        "unconfiguredCount", response.unconfiguredCountries().size()
                )
        );
        return ResponseEntity.ok(CommonResponse.success("Countries workspace", response));
    }

    @GetMapping("/workspace/countries/{countryCode}")
    public ResponseEntity<CommonResponse<AdminCountryWorkspaceDetailResponse>> getCountryWorkspace(@PathVariable String countryCode) {
        AdminSecurityUtils.assertCountryAccess(countryCode);
        AdminCountryWorkspaceDetailResponse response = adminCountryWorkspaceService.getCountryWorkspace(countryCode);
        adminAuditService.logAction(
                AdminSecurityUtils.currentAdminUserId(),
                "GET_COUNTRY_WORKSPACE",
                "COUNTRY_CONFIG_WORKSPACE",
                response.code(),
                Map.of(
                        "status", response.status(),
                        "readinessPercentage", response.readinessPercentage()
                )
        );
        return ResponseEntity.ok(CommonResponse.success("Country workspace", response));
    }

    @GetMapping("/{countryCode}")
    public ResponseEntity<CommonResponse<CountryConfigSnapshotResponse>> getCountryConfig(
            @PathVariable String countryCode,
            @RequestParam(required = false) OffsetDateTime asOf) {
        AdminSecurityUtils.assertCountryAccess(countryCode);
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
        AdminSecurityUtils.assertCountryAccess(countryCode);
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

    @GetMapping("/configuration-status")
    public ResponseEntity<CommonResponse<CountryConfigurationStatusResponse>> getConfigurationStatus() {
        CountryConfigurationStatusResponse response = countryConfigurationStatusService.getConfigurationStatus();
        adminAuditService.logAction(
                AdminSecurityUtils.currentAdminUserId(),
                "GET_COUNTRY_CONFIGURATION_STATUS",
                "COUNTRY_CONFIG",
                null,
                Map.of(
                        "notConfiguredCount", response.notConfigured().size(),
                        "draftOrPartialCount", response.draftOrPartial().size(),
                        "configuredCount", response.configured().size()
                )
        );
        return ResponseEntity.ok(CommonResponse.success("Country configuration status", response));
    }

    @PostMapping("/{countryCode}/initialize")
    public ResponseEntity<CommonResponse<CountryConfigSnapshotResponse>> initializeCountryConfig(@PathVariable String countryCode) {
        AdminSecurityUtils.assertCountryAccess(countryCode);
        UUID adminUserId = AdminSecurityUtils.currentAdminUserId();
        String normalizedCountryCode = countryCode == null ? "" : countryCode.trim().toUpperCase();
        log.info("Country configuration initialize attempt: countryCode={}, adminUserId={}", normalizedCountryCode, adminUserId);

        CountryConfigSnapshotResponse response = countryConfigurationEngineService.initializeCountryConfiguration(countryCode, adminUserId);
        adminAuditService.logAction(
                adminUserId,
                "INITIALIZE_COUNTRY_CONFIG",
                "COUNTRY_CONFIG",
                countryCode,
                Map.of(
                        "initialized", true,
                        "defaultAccountsCreated", true
                )
        );
        return ResponseEntity.ok(CommonResponse.success("Country configuration initialized", response));
    }

    @PutMapping("/{countryCode}/{configType}")
    public ResponseEntity<CommonResponse<CountryConfigVersionResponse>> upsertCountryConfig(
            @PathVariable String countryCode,
            @PathVariable String configType,
            @Valid @RequestBody SecureUpsertCountryConfigRequest request) {
        AdminSecurityUtils.assertCountryAccess(countryCode);
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
        AdminSecurityUtils.assertCountryAccess(countryCode);
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
        AdminSecurityUtils.assertCountryAccess(countryCode);
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

    @PutMapping("/{countryCode}/setup/activate")
    public ResponseEntity<CommonResponse<List<CountryConfigVersionResponse>>> activateCountrySetup(
            @PathVariable String countryCode,
            @Valid @RequestBody SecureActivateCountrySetupRequest request) {
        AdminSecurityUtils.assertCountryAccess(countryCode);
        UUID adminUserId = AdminSecurityUtils.currentAdminUserId();
        OffsetDateTime asOf = request.effectiveDate() == null ? OffsetDateTime.now() : request.effectiveDate();
        AdminConfigMutationSecurityService.ConfigMutationApprovalContext approvalContext =
                adminConfigMutationSecurityService.validateMutationApproval(adminUserId, request.approval());

        List<CountryConfigVersionResponse> responses = new ArrayList<>();
        for (Map.Entry<CountryConfigType, JsonNode> entry : orderedSetupPayloads(request.payloads()).entrySet()) {
            CountryConfigSnapshotResponse beforeSnapshot = countryConfigurationEngineService.getCountryConfiguration(countryCode, asOf);
            JsonNode beforePayload = payloadOf(beforeSnapshot, entry.getKey());
            CountryConfigVersionResponse response = countryConfigurationEngineService.upsertCountryConfiguration(
                    countryCode,
                    entry.getKey(),
                    new UpsertCountryConfigRequest(entry.getValue(), request.effectiveDate()),
                    adminUserId
            );
            adminAuditService.logAction(
                    adminUserId,
                    "UPSERT_COUNTRY_CONFIG",
                    "COUNTRY_CONFIG",
                    response.id().toString(),
                    buildConfigMutationAuditPayload(
                            response.countryCode(),
                            entry.getKey().externalName(),
                            "UPSERT",
                            beforePayload,
                            response.payload(),
                            response.effectiveDate(),
                            response.version(),
                            null,
                            approvalContext
                    )
            );
            responses.add(response);
        }

        return ResponseEntity.ok(CommonResponse.success("Country setup activated", responses));
    }

    @PostMapping("/{countryCode}/validate")
    public ResponseEntity<CommonResponse<CountryConfigValidationResponse>> validateCountrySetup(
            @PathVariable String countryCode,
            @Valid @RequestBody ValidateCountrySetupRequest request) {
        AdminSecurityUtils.assertCountryAccess(countryCode);
        CountryConfigValidationResponse response = countryConfigurationEngineService.validateCountrySetup(
                countryCode,
                orderedSetupPayloads(request.payloads()),
                request.effectiveDate()
        );
        adminAuditService.logAction(
                AdminSecurityUtils.currentAdminUserId(),
                "VALIDATE_COUNTRY_SETUP",
                "COUNTRY_CONFIG",
                countryCode,
                Map.of(
                        "valid", response.valid(),
                        "errorCount", response.errors().size()
                )
        );
        return ResponseEntity.ok(CommonResponse.success("Country setup validation", response));
    }

    @GetMapping("/{countryCode}/readiness")
    public ResponseEntity<CommonResponse<CountryConfigReadinessResponse>> getCountryReadiness(@PathVariable String countryCode) {
        AdminSecurityUtils.assertCountryAccess(countryCode);
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
        AdminSecurityUtils.assertCountryAccess(countryCode);
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

    @PostMapping("/{countryCode}/accounts")
    public ResponseEntity<CommonResponse<CountryChartAccountDetailsResponse>> createCountryAccount(
            @PathVariable String countryCode,
            @Valid @RequestBody CreateCountryChartAccountRequest request) {
        AdminSecurityUtils.assertCountryAccess(countryCode);
        CountryChartAccountDetailsResponse response = countryChartOfAccountsService.createCountryAccount(countryCode, request);
        adminAuditService.logAction(
                AdminSecurityUtils.currentAdminUserId(),
                "CREATE_COUNTRY_CHART_ACCOUNT",
                "COUNTRY_ACCOUNT",
                response.id().toString(),
                Map.of(
                        "countryCode", response.countryCode(),
                        "group", response.groupId().name(),
                        "accountNumber", response.accountNumber()
                )
        );
        return ResponseEntity.ok(CommonResponse.success("Country account created", response));
    }

    @PostMapping("/{countryCode}/accounts/auto-generate")
    public ResponseEntity<CommonResponse<List<CountryChartAccountSummaryResponse>>> autoGenerateCountryAccounts(@PathVariable String countryCode) {
        AdminSecurityUtils.assertCountryAccess(countryCode);
        List<CountryChartAccountSummaryResponse> response = countryChartOfAccountsService.autoGenerateRequiredAccounts(countryCode);
        adminAuditService.logAction(
                AdminSecurityUtils.currentAdminUserId(),
                "AUTO_GENERATE_COUNTRY_CHART_ACCOUNTS",
                "COUNTRY_ACCOUNT",
                countryCode,
                "count=" + response.size()
        );
        return ResponseEntity.ok(CommonResponse.success("Country accounts generated", response));
    }

    @GetMapping("/{countryCode}/accounts/{accountId}")
    public ResponseEntity<CommonResponse<CountryChartAccountDetailsResponse>> getCountryAccount(
            @PathVariable String countryCode,
            @PathVariable UUID accountId) {
        AdminSecurityUtils.assertCountryAccess(countryCode);
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
        AdminSecurityUtils.assertCountryAccess(countryCode);
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

    private Map<CountryConfigType, JsonNode> orderedSetupPayloads(CountrySetupActivationPayloadsRequest payloads) {
        Map<CountryConfigType, JsonNode> ordered = new LinkedHashMap<>();
        ordered.put(CountryConfigType.GENERAL, payloads.general());
        ordered.put(CountryConfigType.ACCOUNTING, payloads.accounting());
        ordered.put(CountryConfigType.MEMBERSHIP, payloads.membership());
        ordered.put(CountryConfigType.MLM_LIMITS, payloads.mlmLimits());
        ordered.put(CountryConfigType.FINANCING, payloads.financing());
        ordered.put(CountryConfigType.GUARANTEE, payloads.guarantee());
        return ordered;
    }

    private CountryConfigType parseConfigType(String rawConfigType) {
        try {
            return CountryConfigType.fromExternalName(rawConfigType);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ex.getMessage());
        }
    }
}
