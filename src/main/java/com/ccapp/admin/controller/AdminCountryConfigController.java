package com.ccapp.admin.controller;

import com.ccapp.admin.audit.AdminAuditService;
import com.ccapp.admin.domain.enums.AdminRole;
import com.ccapp.admin.dto.*;
import com.ccapp.admin.security.AdminSecurityUtils;
import com.ccapp.admin.service.AdminCountryConfigService;
import com.ccapp.common.api.CommonResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminCountryConfigController {

    private final AdminCountryConfigService adminCountryConfigService;
    private final AdminAuditService adminAuditService;

    @GetMapping("/countries")
    public ResponseEntity<CommonResponse<List<CountryConfigResponse>>> listCountries() {
        List<CountryConfigResponse> response = adminCountryConfigService.listCountries();
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "LIST_COUNTRIES", "COUNTRY_CONFIG", null, "count=" + response.size());
        return ResponseEntity.ok(CommonResponse.success("Countries", response));
    }

    @PostMapping("/countries")
    public ResponseEntity<CommonResponse<CountryConfigResponse>> saveCountry(@Valid @RequestBody CountryConfigRequest request) {
        requireSuperOrOperations();
        CountryConfigResponse response = adminCountryConfigService.saveCountry(request);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "SAVE_COUNTRY", "COUNTRY_CONFIG", response.id().toString(), request);
        return ResponseEntity.ok(CommonResponse.success("Country configuration saved", response));
    }

    @PostMapping("/countries/{code}/activate")
    public ResponseEntity<CommonResponse<CountryConfigResponse>> activateCountry(@PathVariable String code) {
        requireSuperOrOperations();
        CountryConfigResponse response = adminCountryConfigService.activateCountry(code);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "ACTIVATE_COUNTRY", "COUNTRY_CONFIG", response.id().toString(), "code=" + code);
        return ResponseEntity.ok(CommonResponse.success("Country activated", response));
    }

    @PostMapping("/countries/{code}/configure")
    public ResponseEntity<CommonResponse<CountryConfigResponse>> configureCountry(@PathVariable String code) {
        requireSuperOrOperations();
        CountryConfigResponse response = adminCountryConfigService.configureCountry(code);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "CONFIGURE_COUNTRY", "COUNTRY_CONFIG", response.id().toString(), "code=" + code);
        return ResponseEntity.ok(CommonResponse.success("Country configured", response));
    }

    @GetMapping("/subscription-plans")
    public ResponseEntity<CommonResponse<List<AdminSubscriptionPlanResponse>>> listPlans(
            @RequestParam(required = false) String country) {
        List<AdminSubscriptionPlanResponse> response = adminCountryConfigService.listSubscriptionPlans(country);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "LIST_SUBSCRIPTION_PLANS", "SUBSCRIPTION_PLAN", null, "count=" + response.size());
        return ResponseEntity.ok(CommonResponse.success("Subscription plans", response));
    }

    @PostMapping("/subscription-plans")
    public ResponseEntity<CommonResponse<List<AdminSubscriptionPlanResponse>>> savePlans(
            @Valid @RequestBody AdminSubscriptionPlanBulkUpsertRequest request) {
        requireSuperOrOperations();
        List<AdminSubscriptionPlanResponse> response = adminCountryConfigService.saveSubscriptionPlans(request);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "UPSERT_SUBSCRIPTION_PLAN_CATALOG", "SUBSCRIPTION_PLAN", request.countryCode(), "count=" + response.size());
        return ResponseEntity.ok(CommonResponse.success("Subscription plan catalog saved", response));
    }

    @PostMapping("/credit-policy")
    public ResponseEntity<CommonResponse<AdminPolicyResponse>> saveCreditPolicy(@Valid @RequestBody AdminPolicyRequest request) {
        requireSuperOrOperations();
        AdminPolicyResponse response = adminCountryConfigService.createCreditPolicy(request);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "SAVE_CREDIT_POLICY", "CREDIT_POLICY", response.id().toString(), request);
        return ResponseEntity.ok(CommonResponse.success("Credit policy saved", response));
    }

    @PostMapping("/seed-capital-policy")
    public ResponseEntity<CommonResponse<AdminPolicyResponse>> saveSeedCapitalPolicy(@Valid @RequestBody AdminPolicyRequest request) {
        requireSuperOrOperations();
        AdminPolicyResponse response = adminCountryConfigService.createSeedCapitalPolicy(request);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "SAVE_SEED_CAPITAL_POLICY", "SEED_CAPITAL_POLICY", response.id().toString(), request);
        return ResponseEntity.ok(CommonResponse.success("Seed capital policy saved", response));
    }

    private void requireSuperOrOperations() {
        AdminRole role = AdminSecurityUtils.currentRole();
        if (role != AdminRole.ROLE_SUPER_ADMIN && role != AdminRole.ROLE_OPERATIONS) {
            throw new AccessDeniedException("Insufficient admin role");
        }
    }
}
