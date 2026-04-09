package com.ccapp.financing.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.api.PageResponse;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.financing.domain.enums.LoanApplicationStatus;
import com.ccapp.financing.dto.CreateLoanApplicationRequest;
import com.ccapp.financing.dto.LoanApplicationResponse;
import com.ccapp.financing.service.FinancingApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/financing/applications")
@RequiredArgsConstructor
@Slf4j
public class FinancingApplicationController {

    private final FinancingApplicationService financingApplicationService;

    @PostMapping
    @Operation(
            summary = "Create loan application",
            description = "Legacy direct client application creation path kept temporarily for compatibility. Future canonical client seed capital flow will converge on credit/* and dedicated eligibility/application aggregates.",
            deprecated = true
    )
    @Deprecated
    public ResponseEntity<CommonResponse<LoanApplicationResponse>> createApplication(
            @Valid @RequestBody CreateLoanApplicationRequest request) {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        UUID actorUserId = resolveActorUserId();
        log.warn("legacy_endpoint_invoked endpointId={} actorUserId={} accountHolderId={} offerId={} circleLinked={}",
                "financing-applications.create-client",
                actorUserId,
                requesterAccountHolderId,
                request.offerId(),
                request.circleId() != null);
        LoanApplicationResponse response = financingApplicationService.createApplication(
                actorUserId,
                requesterAccountHolderId,
                request
        );
        return ResponseEntity.ok(CommonResponse.success("Loan application created", response));
    }

    @GetMapping
    public ResponseEntity<CommonResponse<PageResponse<LoanApplicationResponse>>> listApplications(
            @RequestParam(required = false) UUID partnerId,
            @RequestParam(required = false) LoanApplicationStatus status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        PageResponse<LoanApplicationResponse> response = financingApplicationService.listApplications(
                resolveActorUserId(),
                requesterAccountHolderId,
                partnerId,
                status,
                page,
                size
        );
        return ResponseEntity.ok(CommonResponse.success("Loan applications", response));
    }

    @GetMapping("/{applicationId}")
    public ResponseEntity<CommonResponse<LoanApplicationResponse>> getApplication(@PathVariable UUID applicationId) {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        LoanApplicationResponse response = financingApplicationService.getApplication(
                resolveActorUserId(),
                requesterAccountHolderId,
                applicationId
        );
        return ResponseEntity.ok(CommonResponse.success("Loan application", response));
    }

    private UUID resolveActorUserId() {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        return principal.userId() != null ? principal.userId() : SecurityUtils.currentAccountHolderId();
    }
}
