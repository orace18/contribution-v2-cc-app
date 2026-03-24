package com.ccapp.financing.controller;

import com.ccapp.admin.audit.AdminAuditService;
import com.ccapp.admin.security.AdminSecurityUtils;
import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.api.PageResponse;
import com.ccapp.financing.domain.enums.LoanApplicationStatus;
import com.ccapp.financing.dto.AdminFinancingApplicationDetailsResponse;
import com.ccapp.financing.dto.AdminFinancingApplicationListItemResponse;
import com.ccapp.financing.dto.ReviewLoanApplicationRequest;
import com.ccapp.financing.service.AdminFinancingApplicationService;
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

import java.util.UUID;

@RestController
@RequestMapping({"/api/admin/financing/applications", "/admin/financing/applications"})
@RequiredArgsConstructor
public class AdminFinancingApplicationController {

    private final AdminFinancingApplicationService adminFinancingApplicationService;
    private final AdminAuditService adminAuditService;

    @GetMapping
    public ResponseEntity<CommonResponse<PageResponse<AdminFinancingApplicationListItemResponse>>> listApplications(
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) UUID partnerId,
            @RequestParam(required = false) LoanApplicationStatus status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        PageResponse<AdminFinancingApplicationListItemResponse> response = adminFinancingApplicationService.listApplications(
                countryCode,
                partnerId,
                status,
                page,
                size
        );
        adminAuditService.logAction(
                AdminSecurityUtils.currentAdminUserId(),
                "LIST_FINANCING_APPLICATIONS",
                "LOAN_APPLICATION",
                null,
                "filters(countryCode=" + countryCode + ",partnerId=" + partnerId + ",status=" + status + ")"
        );
        return ResponseEntity.ok(CommonResponse.success("Financing applications", response));
    }

    @GetMapping("/{applicationId}")
    public ResponseEntity<CommonResponse<AdminFinancingApplicationDetailsResponse>> getApplication(@PathVariable UUID applicationId) {
        AdminFinancingApplicationDetailsResponse response = adminFinancingApplicationService.getApplication(applicationId);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "VIEW_FINANCING_APPLICATION", "LOAN_APPLICATION", applicationId.toString(), null);
        return ResponseEntity.ok(CommonResponse.success("Financing application", response));
    }

    @PutMapping("/{applicationId}/review")
    public ResponseEntity<CommonResponse<AdminFinancingApplicationDetailsResponse>> reviewApplication(
            @PathVariable UUID applicationId,
            @Valid @RequestBody ReviewLoanApplicationRequest request) {
        AdminFinancingApplicationDetailsResponse response = adminFinancingApplicationService.reviewApplication(
                AdminSecurityUtils.currentAdminUserId(),
                applicationId,
                request
        );
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "REVIEW_FINANCING_APPLICATION", "LOAN_APPLICATION", applicationId.toString(), request);
        return ResponseEntity.ok(CommonResponse.success("Financing application reviewed", response));
    }
}
