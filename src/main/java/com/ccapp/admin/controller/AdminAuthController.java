package com.ccapp.admin.controller;

import com.ccapp.admin.dto.*;
import com.ccapp.admin.service.AdminAuthService;
import com.ccapp.common.api.CommonResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @PostMapping("/login")
    public ResponseEntity<CommonResponse<AdminLoginResponse>> login(@Valid @RequestBody AdminLoginRequest request) {
        return ResponseEntity.ok(CommonResponse.success("Admin credentials validated", adminAuthService.login(request)));
    }

    @PostMapping("/verify-2fa")
    public ResponseEntity<CommonResponse<AdminTokenResponse>> verifyTwoFactor(@Valid @RequestBody AdminVerifyTwoFactorRequest request) {
        return ResponseEntity.ok(CommonResponse.success("Admin authentication successful", adminAuthService.verifyTwoFactor(request)));
    }

    @PostMapping("/setup-2fa")
    public ResponseEntity<CommonResponse<AdminSetupTwoFactorResponse>> setupTwoFactor(@Valid @RequestBody AdminSetupTwoFactorRequest request) {
        return ResponseEntity.ok(CommonResponse.success("Admin 2FA setup processed", adminAuthService.setupTwoFactor(request)));
    }

    @PostMapping("/accept-invitation")
    public ResponseEntity<CommonResponse<AdminAcceptInvitationResponse>> acceptInvitation(
            @Valid @RequestBody AdminAcceptInvitationRequest request) {
        return ResponseEntity.ok(CommonResponse.success("Invitation accepted", adminAuthService.acceptInvitation(request)));
    }
}
