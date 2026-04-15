package com.ccapp.kyc.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.kyc.domain.IdentityVerificationEntity;
import com.ccapp.kyc.domain.VerificationStatus;
import com.ccapp.kyc.dto.AdminVerificationReviewRequest;
import com.ccapp.kyc.dto.VerificationResponse;
import com.ccapp.kyc.repository.IdentityVerificationRepository;
import com.ccapp.kyc.repository.VerificationAnalysisRepository;
import com.ccapp.kyc.service.IdentityVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin — Identity Verification", description = "Back-office management of identity verifications")
@RestController
@RequestMapping("/api/admin/kyc/identity-verifications")
@RequiredArgsConstructor
public class AdminIdentityVerificationController {

    private final IdentityVerificationService identityVerificationService;
    private final IdentityVerificationRepository identityVerificationRepository;
    private final VerificationAnalysisRepository verificationAnalysisRepository;

    @Operation(summary = "List all verifications", description = "Optionally filter by status")
    @GetMapping
    public ResponseEntity<CommonResponse<List<VerificationResponse>>> list(
            @RequestParam(required = false) VerificationStatus status) {
        List<IdentityVerificationEntity> verifications = identityVerificationService.listVerifications(status);
        List<VerificationResponse> responses = verifications.stream()
                .map(v -> identityVerificationService.toAdminResponse(
                        v, verificationAnalysisRepository.findByVerification(v).orElse(null)))
                .toList();
        return ResponseEntity.ok(CommonResponse.success("Identity verifications", responses));
    }

    @Operation(summary = "Get full details for a verification (unmasked)")
    @GetMapping("/{verificationId}")
    public ResponseEntity<CommonResponse<VerificationResponse>> get(@PathVariable UUID verificationId) {
        IdentityVerificationEntity verification = identityVerificationRepository.findById(verificationId)
                .orElseThrow(() -> new NotFoundException("Identity verification not found"));
        VerificationResponse response = identityVerificationService.toAdminResponse(
                verification, verificationAnalysisRepository.findByVerification(verification).orElse(null));
        return ResponseEntity.ok(CommonResponse.success("Identity verification", response));
    }

    @Operation(summary = "Manually review a verification (APPROVED, REJECTED, REVIEW)")
    @PostMapping("/{verificationId}/review")
    public ResponseEntity<CommonResponse<VerificationResponse>> review(
            @PathVariable UUID verificationId,
            @Valid @RequestBody AdminVerificationReviewRequest request) {
        VerificationResponse response = identityVerificationService.reviewVerification(verificationId, request);
        return ResponseEntity.ok(CommonResponse.success("Verification reviewed", response));
    }
}
