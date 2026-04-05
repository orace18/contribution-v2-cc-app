package com.ccapp.identity.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.identity.dto.KycStatusResponse;
import com.ccapp.identity.service.KycStatusConsistencyService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/kyc")
@RequiredArgsConstructor
@Tag(name = "KYC", description = "Current KYC status endpoints")
@SecurityRequirement(name = "bearerAuth")
public class KycStatusController {

    private final KycStatusConsistencyService kycStatusConsistencyService;

    @GetMapping("/status")
    @Operation(summary = "Fetch current KYC status for the authenticated account holder")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<KycStatusResponse>> getStatus() {
        var accountHolderId = SecurityUtils.currentAccountHolderId();
        var kycStatus = kycStatusConsistencyService.reconcileAccountHolderStatus(accountHolderId);
        var kycLevel = kycStatusConsistencyService.resolveAccountHolderLevel(accountHolderId);
        return ResponseEntity.ok(CommonResponse.success("KYC status", new KycStatusResponse(kycStatus, kycLevel)));
    }
}
