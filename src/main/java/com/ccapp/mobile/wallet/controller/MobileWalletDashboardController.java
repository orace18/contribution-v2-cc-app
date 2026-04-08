package com.ccapp.mobile.wallet.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.identity.managedaccounts.service.ManagedAccountReadContextService;
import com.ccapp.wallet.dto.WalletDashboardOperationResponse;
import com.ccapp.wallet.dto.WalletDashboardResponse;
import com.ccapp.wallet.service.WalletDashboardService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/mobile/wallet")
@RequiredArgsConstructor
@Tag(name = "Mobile Wallet", description = "Wallet dashboard for mobile clients")
@SecurityRequirement(name = "bearerAuth")
public class MobileWalletDashboardController {

    private final WalletDashboardService walletDashboardService;
    private final ManagedAccountReadContextService managedAccountReadContextService;

    @GetMapping("/dashboard")
    @Operation(summary = "Get wallet dashboard")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account holder not found", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<WalletDashboardResponse>> dashboard(
            @Parameter(description = "Optional target accountHolderId for an authorized managed account")
            @RequestParam(name = "accountHolderId", required = false) UUID requestedAccountHolderId) {
        UUID actorUserId = resolveActorUserId();
        UUID accountHolderId = managedAccountReadContextService.resolveViewTargetAccountHolderId(
                actorUserId,
                SecurityUtils.currentAccountHolderId(),
                requestedAccountHolderId
        );
        return ResponseEntity.ok(CommonResponse.success(
                "Wallet dashboard",
                walletDashboardService.getDashboard(actorUserId, accountHolderId)
        ));
    }

    @GetMapping("/operations")
    @Operation(summary = "Get wallet operations")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account holder not found", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<List<WalletDashboardOperationResponse>>> operations(
            @Parameter(description = "Optional target accountHolderId for an authorized managed account")
            @RequestParam(name = "accountHolderId", required = false) UUID requestedAccountHolderId) {
        UUID actorUserId = resolveActorUserId();
        UUID accountHolderId = managedAccountReadContextService.resolveViewTargetAccountHolderId(
                actorUserId,
                SecurityUtils.currentAccountHolderId(),
                requestedAccountHolderId
        );
        return ResponseEntity.ok(CommonResponse.success(
                "Wallet operations",
                walletDashboardService.getRecentOperations(actorUserId, accountHolderId)
        ));
    }

    private UUID resolveActorUserId() {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        return principal.userId() != null ? principal.userId() : SecurityUtils.currentAccountHolderId();
    }
}
