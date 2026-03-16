package com.ccapp.financing.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.api.PageResponse;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.financing.domain.enums.LoanApplicationStatus;
import com.ccapp.financing.dto.LoanApplicationResponse;
import com.ccapp.financing.dto.ReviewLoanApplicationRequest;
import com.ccapp.financing.service.FinancingApplicationService;
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
@RequestMapping("/partner/backoffice/applications")
@RequiredArgsConstructor
public class PartnerBackofficeApplicationController {

    private final FinancingApplicationService financingApplicationService;

    @GetMapping
    public ResponseEntity<CommonResponse<PageResponse<LoanApplicationResponse>>> listPartnerApplications(
            @RequestParam UUID partnerId,
            @RequestParam(required = false) LoanApplicationStatus status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        PageResponse<LoanApplicationResponse> response = financingApplicationService.listApplications(
                resolveActorUserId(),
                accountHolderId,
                partnerId,
                status,
                page,
                size
        );
        return ResponseEntity.ok(CommonResponse.success("Partner applications", response));
    }

    @GetMapping("/{applicationId}")
    public ResponseEntity<CommonResponse<LoanApplicationResponse>> getPartnerApplication(@PathVariable UUID applicationId) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        LoanApplicationResponse response = financingApplicationService.getApplication(
                resolveActorUserId(),
                accountHolderId,
                applicationId
        );
        return ResponseEntity.ok(CommonResponse.success("Partner application", response));
    }

    @PutMapping("/{applicationId}/review")
    public ResponseEntity<CommonResponse<LoanApplicationResponse>> reviewApplication(
            @PathVariable UUID applicationId,
            @Valid @RequestBody ReviewLoanApplicationRequest request) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        LoanApplicationResponse response = financingApplicationService.reviewApplication(
                accountHolderId,
                applicationId,
                request
        );
        return ResponseEntity.ok(CommonResponse.success("Loan application updated", response));
    }

    private UUID resolveActorUserId() {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        return principal.userId() != null ? principal.userId() : SecurityUtils.currentAccountHolderId();
    }
}
