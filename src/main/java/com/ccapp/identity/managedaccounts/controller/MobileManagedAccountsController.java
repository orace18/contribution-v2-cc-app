package com.ccapp.identity.managedaccounts.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.identity.managedaccounts.dto.CreateManagedAccountRequest;
import com.ccapp.identity.managedaccounts.dto.LinkExistingManagedAccountRequest;
import com.ccapp.identity.managedaccounts.dto.ManagedAccountDetailResponse;
import com.ccapp.identity.managedaccounts.dto.ManagedAccountSummaryResponse;
import com.ccapp.identity.managedaccounts.dto.UpdateManagedAccountRequest;
import com.ccapp.identity.managedaccounts.service.ManagedAccountsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/mobile/managed-accounts")
@RequiredArgsConstructor
@Tag(name = "Mobile Managed Accounts", description = "Managed accounts for account holders without smartphone access")
@SecurityRequirement(name = "bearerAuth")
public class MobileManagedAccountsController {

    private final ManagedAccountsService managedAccountsService;

    @GetMapping
    @Operation(summary = "List managed accounts for the authenticated app user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<List<ManagedAccountSummaryResponse>>> listManagedAccounts() {
        return ResponseEntity.ok(CommonResponse.success(
                "Managed accounts",
                managedAccountsService.listManagedAccounts(SecurityUtils.currentUserId())
        ));
    }

    @GetMapping("/{accountHolderId}")
    @Operation(summary = "Get managed account detail")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Managed account not found", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<ManagedAccountDetailResponse>> getManagedAccount(@PathVariable UUID accountHolderId) {
        return ResponseEntity.ok(CommonResponse.success(
                "Managed account",
                managedAccountsService.getManagedAccountDetail(SecurityUtils.currentUserId(), accountHolderId)
        ));
    }

    @PostMapping
    @Operation(summary = "Create a managed account for someone without smartphone access")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation or business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "409", description = "Phone number conflict", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<ManagedAccountDetailResponse>> createManagedAccount(
            @Valid @RequestBody CreateManagedAccountRequest request) {
        return ResponseEntity.ok(CommonResponse.success(
                "Managed account created",
                managedAccountsService.createManagedAccount(
                        SecurityUtils.currentUserId(),
                        SecurityUtils.currentAccountHolderId(),
                        request
                )
        ));
    }

    @PostMapping("/link-existing")
    @Operation(summary = "Link an existing assisted account to the authenticated manager after phone verification")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation or business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "409", description = "Managed account already linked to another manager", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<ManagedAccountDetailResponse>> linkExistingManagedAccount(
            @Valid @RequestBody LinkExistingManagedAccountRequest request) {
        return ResponseEntity.ok(CommonResponse.success(
                "Managed account linked",
                managedAccountsService.linkExistingManagedAccount(
                        SecurityUtils.currentUserId(),
                        SecurityUtils.currentAccountHolderId(),
                        request
                )
        ));
    }

    @PatchMapping("/{accountHolderId}")
    @Operation(summary = "Update basic civil info for a managed account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation or business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Managed account not found", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<ManagedAccountDetailResponse>> updateManagedAccount(
            @PathVariable UUID accountHolderId,
            @Valid @RequestBody UpdateManagedAccountRequest request) {
        return ResponseEntity.ok(CommonResponse.success(
                "Managed account updated",
                managedAccountsService.updateManagedAccount(
                        SecurityUtils.currentUserId(),
                        SecurityUtils.currentAccountHolderId(),
                        accountHolderId,
                        request
                )
        ));
    }

    @PostMapping("/{accountHolderId}/revoke")
    @Operation(summary = "Revoke manager access to a managed account without deleting the account holder")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Managed account not found", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<Void>> revokeManagedAccount(@PathVariable UUID accountHolderId) {
        managedAccountsService.revokeManagedAccount(SecurityUtils.currentUserId(), accountHolderId);
        return ResponseEntity.ok(CommonResponse.<Void>success("Managed account revoked", null));
    }
}
