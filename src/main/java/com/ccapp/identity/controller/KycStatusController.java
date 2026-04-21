package com.ccapp.identity.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.identity.dto.KycStatusResponse;
import com.ccapp.identity.managedaccounts.service.ManagedAccountReadContextService;
import com.ccapp.identity.service.KycStatusConsistencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;

import com.ccapp.cpass.kyc.CPassKycGateway;
import com.ccapp.cpass.kyc.CPassKycStatusResponse;

import java.util.UUID;

@RestController
@RequestMapping("/kyc")
@RequiredArgsConstructor
@Tag(name = "KYC", description = "Current KYC status endpoints")
@SecurityRequirement(name = "bearerAuth")
public class KycStatusController {

    private final KycStatusConsistencyService kycStatusConsistencyService;
    private final ManagedAccountReadContextService managedAccountReadContextService;

    @Nullable
    @Autowired(required = false)
    private CPassKycGateway cPassKycGateway;

    @GetMapping("/status")
    @Operation(summary = "Fetch current KYC status for the authenticated account holder")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<KycStatusResponse>> getStatus(
            @Parameter(description = "Optional target accountHolderId for an authorized managed account")
            @RequestParam(name = "accountHolderId", required = false) UUID requestedAccountHolderId,
            @Parameter(description = "Optional CPaaS caseId to retrieve dynamic tracking status")
            @RequestParam(name = "caseId", required = false) String caseId) {
        UUID actorUserId = SecurityUtils.currentUserId();
        var accountHolderId = managedAccountReadContextService.resolveViewTargetAccountHolderId(
                actorUserId,
                SecurityUtils.currentAccountHolderId(),
                requestedAccountHolderId
        );
        var kycStatus = kycStatusConsistencyService.reconcileAccountHolderStatus(accountHolderId);
        var kycLevel = kycStatusConsistencyService.resolveAccountHolderLevel(accountHolderId);
        
        String cpassProductStatus = null;
        String cpassReasonCode = null;
        String cpassNextAction = null;
        String cpassStatusReason = null;
        String cpassRecommendation = null;
        Double cpassConfidenceScore = null;

        if (caseId != null && !caseId.isBlank() && cPassKycGateway != null) {
             CPassKycStatusResponse cpassRes = cPassKycGateway.getKycStatus(caseId);
             if (cpassRes != null) {
                 cpassProductStatus = cpassRes.productStatus();
                 cpassReasonCode = cpassRes.reasonCode();
                 cpassNextAction = cpassRes.nextAction();
                 cpassStatusReason = cpassRes.statusReason();
                 cpassRecommendation = cpassRes.recommendation();
                 cpassConfidenceScore = cpassRes.confidenceScore();
             }
        }
        
        return ResponseEntity.ok(CommonResponse.success("KYC status", new KycStatusResponse(
            kycStatus, kycLevel, caseId, cpassProductStatus, cpassReasonCode, cpassNextAction,
            cpassStatusReason, cpassRecommendation, cpassConfidenceScore
        )));
    }
}
