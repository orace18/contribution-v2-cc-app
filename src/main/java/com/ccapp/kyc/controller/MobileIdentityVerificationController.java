package com.ccapp.kyc.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.kyc.dto.InitVerificationRequest;
import com.ccapp.kyc.dto.InitVerificationResponse;
import com.ccapp.kyc.dto.UploadVerificationResponse;
import com.ccapp.kyc.dto.VerificationResponse;
import com.ccapp.kyc.service.IdentityVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Identity verification API for mobile clients.
 *
 * <p>Flow:
 * <ol>
 *   <li>POST /init — create a session, get verificationId
 *   <li>POST /{verificationId}/upload — upload document + selfie (raw files only)
 *   <li>GET  /{verificationId} — poll for result
 * </ol>
 *
 * <p>All OCR, face matching, and scoring are performed server-side.
 * Clients must never send pre-computed hashes, OCR results, or face scores.
 */
@Tag(name = "Mobile — Identity Verification", description = "Server-side KYC: OCR, face match, document validity")
@RestController
@RequestMapping("/api/mobile/kyc/identity-verification")
@RequiredArgsConstructor
public class MobileIdentityVerificationController {

    private final IdentityVerificationService identityVerificationService;

    @Operation(summary = "Initialize a verification session")
    @PostMapping("/init")
    public ResponseEntity<CommonResponse<InitVerificationResponse>> init(
            @Valid @RequestBody InitVerificationRequest request) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        InitVerificationResponse response = identityVerificationService.init(accountHolderId, request);
        return ResponseEntity.ok(CommonResponse.success("Verification session created", response));
    }

    @Operation(
            summary = "Upload document and selfie",
            description = "Upload raw files only. Processing is async — poll GET /{verificationId} for the result."
    )
    @PostMapping(value = "/{verificationId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CommonResponse<UploadVerificationResponse>> upload(
            @PathVariable UUID verificationId,
            @RequestPart("documentFront") MultipartFile documentFront,
            @RequestPart(value = "documentBack", required = false) MultipartFile documentBack,
            @RequestPart("selfie") MultipartFile selfie,
            @RequestParam(required = false) String captureDevice,
            @RequestParam(required = false) String appVersion,
            @RequestParam(required = false) String platform) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        UploadVerificationResponse response = identityVerificationService.upload(
                accountHolderId, verificationId, documentFront, documentBack, selfie,
                captureDevice, appVersion, platform);
        return ResponseEntity.ok(CommonResponse.success("Files accepted, processing started", response));
    }

    @Operation(
            summary = "Get verification result",
            description = "Status flow: INITIATED → PROCESSING → APPROVED | REVIEW | REJECTED"
    )
    @GetMapping("/{verificationId}")
    public ResponseEntity<CommonResponse<VerificationResponse>> get(@PathVariable UUID verificationId) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        VerificationResponse response = identityVerificationService.getVerification(accountHolderId, verificationId);
        return ResponseEntity.ok(CommonResponse.success("Verification result", response));
    }
}
