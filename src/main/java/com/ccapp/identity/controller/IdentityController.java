package com.ccapp.identity.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.identity.dto.*;
import com.ccapp.identity.service.FirebaseUserClaims;
import com.ccapp.identity.service.IdentityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/identity")
@RequiredArgsConstructor
public class IdentityController {

    private final IdentityService identityService;

    @PostMapping("/sync")
    public ResponseEntity<CommonResponse<SyncResponse>> sync(@Valid @RequestBody SyncRequest request, Authentication authentication) {
        FirebaseUserClaims claims = toClaims(authentication);
        return ResponseEntity.ok(CommonResponse.success("Identity synced", identityService.sync(claims, request)));
    }

    @PostMapping("/onboarding")
    public ResponseEntity<CommonResponse<MeResponse>> onboarding(@Valid @RequestBody OnboardingRequest request) {
        UUID userId = SecurityUtils.currentUserId();
        return ResponseEntity.ok(CommonResponse.success("Onboarding updated", identityService.onboarding(userId, request)));
    }

    @GetMapping("/me")
    public ResponseEntity<CommonResponse<MeResponse>> me() {
        UUID userId = SecurityUtils.currentUserId();
        return ResponseEntity.ok(CommonResponse.success("Current user", identityService.me(userId)));
    }

    @PostMapping("/devices")
    public ResponseEntity<CommonResponse<UserDeviceDto>> registerDevice(@Valid @RequestBody RegisterDeviceRequest request) {
        UUID userId = SecurityUtils.currentUserId();
        return ResponseEntity.ok(CommonResponse.success("Device upserted", identityService.registerDevice(userId, request)));
    }

    @PostMapping("/devices/{deviceId}/block")
    public ResponseEntity<CommonResponse<Void>> blockDevice(@PathVariable UUID deviceId) {
        UUID userId = SecurityUtils.currentUserId();
        identityService.blockDevice(userId, deviceId);
        return ResponseEntity.ok(CommonResponse.success("Device blocked"));
    }

    @PostMapping("/sessions/refresh")
    public ResponseEntity<CommonResponse<RefreshTokenResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(CommonResponse.success("Refresh token rotated", identityService.rotateRefreshToken(request)));
    }

    @PostMapping("/sessions/{sessionId}/revoke")
    public ResponseEntity<CommonResponse<Void>> revokeSession(@PathVariable UUID sessionId) {
        UUID userId = SecurityUtils.currentUserId();
        identityService.revokeSession(userId, sessionId);
        return ResponseEntity.ok(CommonResponse.success("Session revoked"));
    }

    private FirebaseUserClaims toClaims(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
            throw new IllegalStateException("Invalid authentication context");
        }
        return new FirebaseUserClaims(principal.firebaseUid(), principal.email());
    }
}
