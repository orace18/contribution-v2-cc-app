package com.ccapp.media.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.managedaccounts.service.ManagedAccountReadContextService;
import com.ccapp.media.domain.dto.DeclareIdentityRequest;
import com.ccapp.media.domain.dto.KycDraftResponse;
import com.ccapp.media.domain.dto.SubmitKycRequest;
import com.ccapp.media.domain.dto.SubmitKycResponse;
import com.ccapp.media.service.KycDraftService;
import com.ccapp.media.service.KycSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/mobile/kyc")
@RequiredArgsConstructor
@Tag(name = "Mobile KYC", description = "KYC submission for mobile and delegated flows")
@SecurityRequirement(name = "bearerAuth")
public class MobileKycController {

    private final KycSubmissionService kycSubmissionService;
    private final KycDraftService kycDraftService;
    private final AccountHolderService accountHolderService;
    private final ManagedAccountReadContextService managedAccountReadContextService;

    @GetMapping("/draft")
    @Operation(
            summary = "Get the current KYC draft for an account holder",
            description = "Returns the identity fields last saved via /declare-identity and the " +
                    "media IDs of documents already uploaded, so the mobile client can restore " +
                    "a partially completed KYC flow without re-entering data. " +
                    "identityDeclared=false means step 1 (identity form) has not been completed yet."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Draft retrieved",
                    content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account holder not found",
                    content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<KycDraftResponse>> getDraft(
            @RequestParam(required = false) UUID accountHolderId) {
        UUID actorUserId = SecurityUtils.currentUserId();
        UUID resolved = managedAccountReadContextService.resolveViewTargetAccountHolderId(
                actorUserId,
                SecurityUtils.currentAccountHolderId(),
                accountHolderId
        );
        KycDraftResponse draft = kycDraftService.getDraft(resolved);
        return ResponseEntity.ok(CommonResponse.success("KYC draft", draft));
    }

    @PostMapping("/declare-identity")
    @Operation(summary = "Declare civil identity before uploading KYC documents",
            description = "Stores firstName, lastName, birthDate, nationality, documentType and documentNumber " +
                    "on the account holder. Must be called before document upload so the back-office can " +
                    "compare declared identity against uploaded documents.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Identity declared",
                    content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account holder not found",
                    content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<Void>> declareIdentity(
            @Valid @RequestBody DeclareIdentityRequest request) {
        UUID actorUserId = SecurityUtils.currentUserId();
        UUID resolvedAccountHolderId = managedAccountReadContextService.resolveViewTargetAccountHolderId(
                actorUserId,
                SecurityUtils.currentAccountHolderId(),
                request.accountHolderId()
        );
        accountHolderService.declareIdentity(
                resolvedAccountHolderId,
                request.firstName(),
                request.lastName(),
                request.birthDate(),
                request.nationality(),
                request.documentType(),
                request.documentNumber()
        );
        return ResponseEntity.ok(CommonResponse.success("Identity declared", null));
    }

    @PostMapping("/submit")
    @Operation(summary = "Submit minimal KYC for an account holder")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Success",
                    content = @Content(
                            schema = @Schema(implementation = CommonResponse.class),
                            examples = @ExampleObject(value = "{\"success\":true,\"message\":\"KYC submitted\",\"data\":{\"accountHolderId\":\"8df991be-c9f2-45a5-9a2d-36dd6204aff3\",\"kycStatus\":\"VERIFIED\",\"kycLevel\":\"LEVEL_1_BASIC\",\"submittedAt\":\"2026-03-10T11:22:00Z\"},\"timestamp\":\"2026-03-10T11:22:00Z\"}")
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Validation or business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account holder not found", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<SubmitKycResponse>> submit(@Valid @RequestBody SubmitKycRequest request) {
        UUID actorAccountHolderId = SecurityUtils.currentAccountHolderId();
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        UUID actorUserId = principal.userId() != null ? principal.userId() : actorAccountHolderId;

        SubmitKycResponse response = kycSubmissionService.submit(actorUserId, actorAccountHolderId, request.accountHolderId());
        return ResponseEntity.ok(CommonResponse.success("KYC submitted", response));
    }
}
