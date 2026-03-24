package com.ccapp.financing.controller;

import com.ccapp.admin.audit.AdminAuditService;
import com.ccapp.admin.security.AdminSecurityUtils;
import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.api.PageResponse;
import com.ccapp.financing.domain.enums.FinancialPartnerStatus;
import com.ccapp.financing.domain.enums.FinancialPartnerType;
import com.ccapp.financing.dto.CreateFinancialPartnerRequest;
import com.ccapp.financing.dto.FinancialPartnerResponse;
import com.ccapp.financing.dto.UpdateFinancialPartnerStatusRequest;
import com.ccapp.financing.dto.UpdateFinancialPartnerRequest;
import com.ccapp.financing.service.FinancialPartnerAdminService;
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

import java.util.UUID;

@RestController
@RequestMapping({"/api/admin/partners", "/admin/partners"})
@RequiredArgsConstructor
public class AdminFinancialPartnerController {

    private final FinancialPartnerAdminService financialPartnerAdminService;
    private final AdminAuditService adminAuditService;

    @GetMapping
    public ResponseEntity<CommonResponse<PageResponse<FinancialPartnerResponse>>> listPartners(
            @RequestParam(required = false) FinancialPartnerStatus status,
            @RequestParam(required = false) FinancialPartnerType type,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        PageResponse<FinancialPartnerResponse> response = financialPartnerAdminService.listPartners(
                status,
                type,
                countryCode,
                search,
                page,
                size
        );
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "LIST_FINANCIAL_PARTNERS", "FINANCIAL_PARTNER", null,
                "filters(status=" + status + ",type=" + type + ",country=" + countryCode + ")");
        return ResponseEntity.ok(CommonResponse.success("Financial partners", response));
    }

    @PostMapping
    public ResponseEntity<CommonResponse<FinancialPartnerResponse>> createPartner(@Valid @RequestBody CreateFinancialPartnerRequest request) {
        FinancialPartnerResponse response = financialPartnerAdminService.createPartner(request);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "CREATE_FINANCIAL_PARTNER", "FINANCIAL_PARTNER", response.id().toString(), request);
        return ResponseEntity.ok(CommonResponse.success("Financial partner created", response));
    }

    @PutMapping("/{partnerId}")
    public ResponseEntity<CommonResponse<FinancialPartnerResponse>> updatePartner(@PathVariable UUID partnerId,
                                                                                   @Valid @RequestBody UpdateFinancialPartnerRequest request) {
        FinancialPartnerResponse response = financialPartnerAdminService.updatePartner(partnerId, request);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "UPDATE_FINANCIAL_PARTNER", "FINANCIAL_PARTNER", partnerId.toString(), request);
        return ResponseEntity.ok(CommonResponse.success("Financial partner updated", response));
    }

    @PutMapping("/{partnerId}/status")
    public ResponseEntity<CommonResponse<FinancialPartnerResponse>> updatePartnerStatus(@PathVariable UUID partnerId,
                                                                                         @Valid @RequestBody UpdateFinancialPartnerStatusRequest request) {
        FinancialPartnerResponse response = financialPartnerAdminService.updatePartnerStatus(partnerId, request.status());
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "UPDATE_FINANCIAL_PARTNER_STATUS", "FINANCIAL_PARTNER", partnerId.toString(), request);
        return ResponseEntity.ok(CommonResponse.success("Financial partner status updated", response));
    }

    @GetMapping("/{partnerId}")
    public ResponseEntity<CommonResponse<FinancialPartnerResponse>> getPartner(@PathVariable UUID partnerId) {
        FinancialPartnerResponse response = financialPartnerAdminService.getPartner(partnerId);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "VIEW_FINANCIAL_PARTNER", "FINANCIAL_PARTNER", partnerId.toString(), null);
        return ResponseEntity.ok(CommonResponse.success("Financial partner", response));
    }
}
