package com.ccapp.identity.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.identity.verification.dto.ConfirmPhoneVerificationRequest;
import com.ccapp.identity.verification.dto.FirebasePhoneVerificationRequest;
import com.ccapp.identity.verification.dto.FirebasePhoneVerificationResponse;
import com.ccapp.identity.verification.dto.PhoneVerificationResponse;
import com.ccapp.identity.verification.dto.StartPhoneVerificationRequest;
import com.ccapp.identity.verification.service.PhoneVerificationService;
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
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/mobile/phone-verifications")
@RequiredArgsConstructor
@Tag(name = "Mobile Phone Verification", description = "OTP phone verification through SMS or WhatsApp")
@SecurityRequirement(name = "bearerAuth")
public class MobilePhoneVerificationController {

    private final PhoneVerificationService phoneVerificationService;

    @PostMapping("/firebase")
    @Operation(summary = "Confirm phone verification using Firebase Phone Auth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation or business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<FirebasePhoneVerificationResponse>> verifyFirebase(@Valid @RequestBody FirebasePhoneVerificationRequest request) {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        UUID requesterUserId = SecurityUtils.currentUserId();
        FirebasePhoneVerificationResponse response = phoneVerificationService.verifyFirebasePhone(
                requesterAccountHolderId,
                requesterUserId,
                request
        );
        return ResponseEntity.ok(CommonResponse.success("Phone verification confirmed", response));
    }

    @PostMapping("/start")
    @Operation(summary = "Start phone verification")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation or business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<PhoneVerificationResponse>> start(@Valid @RequestBody StartPhoneVerificationRequest request) {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        PhoneVerificationResponse response = phoneVerificationService.startVerification(requesterAccountHolderId, request);
        return ResponseEntity.ok(CommonResponse.success("Phone verification started", response));
    }

    @PostMapping("/{verificationId}/confirm")
    @Operation(summary = "Confirm OTP code")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation or business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Verification not found", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<PhoneVerificationResponse>> confirm(@PathVariable UUID verificationId,
                                                                             @Valid @RequestBody ConfirmPhoneVerificationRequest request) {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        PhoneVerificationResponse response = phoneVerificationService.confirmVerification(requesterAccountHolderId, verificationId, request);
        return ResponseEntity.ok(CommonResponse.success("Phone verification confirmed", response));
    }

    @PostMapping("/{verificationId}/resend")
    @Operation(summary = "Resend OTP code")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation or business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Verification not found", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<PhoneVerificationResponse>> resend(@PathVariable UUID verificationId) {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        PhoneVerificationResponse response = phoneVerificationService.resendVerification(requesterAccountHolderId, verificationId);
        return ResponseEntity.ok(CommonResponse.success("Phone verification code resent", response));
    }

    @GetMapping("/{verificationId}")
    @Operation(summary = "Get phone verification status")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Verification not found", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<PhoneVerificationResponse>> get(@PathVariable UUID verificationId) {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        PhoneVerificationResponse response = phoneVerificationService.getVerification(requesterAccountHolderId, verificationId);
        return ResponseEntity.ok(CommonResponse.success("Phone verification status", response));
    }
}
