package com.ccapp.media.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.media.domain.dto.SubmitKycRequest;
import com.ccapp.media.domain.dto.SubmitKycResponse;
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
