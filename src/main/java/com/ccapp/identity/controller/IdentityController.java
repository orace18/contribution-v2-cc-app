package com.ccapp.identity.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.identity.dto.*;
import com.ccapp.identity.service.FirebaseUserClaims;
import com.ccapp.identity.service.IdentityService;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/identity")
@RequiredArgsConstructor
@Tag(name = "Identity", description = "Gestion de l'identite, devices et sessions utilisateur")
@SecurityRequirement(name = "bearerAuth")
public class IdentityController {

    private final IdentityService identityService;

    @PostMapping("/sync")
    @Operation(summary = "Synchroniser l'identite utilisateur")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Erreur de validation/regle metier", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token invalide ou absent", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<SyncResponse>> sync(@Valid @RequestBody SyncRequest request, Authentication authentication) {
        FirebaseUserClaims claims = toClaims(authentication);
        return ResponseEntity.ok(CommonResponse.success("Identity synced", identityService.sync(claims, request)));
    }

    @PostMapping("/onboarding")
    @Operation(summary = "Mettre a jour les informations d'onboarding")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation invalide", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<MeResponse>> onboarding(@Valid @RequestBody OnboardingRequest request) {
        UUID userId = SecurityUtils.currentUserId();
        return ResponseEntity.ok(CommonResponse.success("Onboarding updated", identityService.onboarding(userId, request)));
    }

    @GetMapping("/onboarding")
    @Operation(summary = "Recuperer le statut d'onboarding multi-etapes")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<OnboardingStatusResponse>> onboardingStatus() {
        UUID userId = SecurityUtils.currentUserId();
        return ResponseEntity.ok(CommonResponse.success("Onboarding status", identityService.getOnboardingStatus(userId)));
    }

    @PatchMapping("/onboarding/step-1")
    @Operation(summary = "Onboarding etape 1: profil")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation invalide", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<OnboardingStatusResponse>> onboardingStep1(@Valid @RequestBody OnboardingStep1Request request) {
        UUID userId = SecurityUtils.currentUserId();
        return ResponseEntity.ok(CommonResponse.success("Onboarding step 1 updated", identityService.onboardingStep1(userId, request)));
    }

    @PatchMapping("/onboarding/step-2")
    @Operation(summary = "Onboarding etape 2: activite")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation invalide", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<OnboardingStatusResponse>> onboardingStep2(@Valid @RequestBody OnboardingStep2Request request) {
        UUID userId = SecurityUtils.currentUserId();
        return ResponseEntity.ok(CommonResponse.success("Onboarding step 2 updated", identityService.onboardingStep2(userId, request)));
    }

    @PatchMapping("/onboarding/step-3")
    @Operation(summary = "Onboarding etape 3: capital et consentements")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation invalide", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<OnboardingStatusResponse>> onboardingStep3(@Valid @RequestBody OnboardingStep3Request request) {
        UUID userId = SecurityUtils.currentUserId();
        return ResponseEntity.ok(CommonResponse.success("Onboarding step 3 updated", identityService.onboardingStep3(userId, request)));
    }

    @GetMapping("/me")
    @Operation(summary = "Recuperer le profil de l'utilisateur courant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<MeResponse>> me() {
        UUID userId = SecurityUtils.currentUserId();
        return ResponseEntity.ok(CommonResponse.success("Current user", identityService.me(userId)));
    }

    @PostMapping("/devices")
    @Operation(summary = "Enregistrer ou mettre a jour un device")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation invalide", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<UserDeviceDto>> registerDevice(@Valid @RequestBody RegisterDeviceRequest request) {
        UUID userId = SecurityUtils.currentUserId();
        return ResponseEntity.ok(CommonResponse.success("Device upserted", identityService.registerDevice(userId, request)));
    }

    @PostMapping("/devices/{deviceId}/block")
    @Operation(summary = "Bloquer un device utilisateur")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non autorise", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Device introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<Void>> blockDevice(@PathVariable UUID deviceId) {
        UUID userId = SecurityUtils.currentUserId();
        identityService.blockDevice(userId, deviceId);
        return ResponseEntity.ok(CommonResponse.success("Device blocked"));
    }

    @PostMapping("/sessions/refresh")
    @Operation(summary = "Faire une rotation du refresh token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation invalide", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Refresh token invalide/expire", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<RefreshTokenResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(CommonResponse.success("Refresh token rotated", identityService.rotateRefreshToken(request)));
    }

    @PostMapping("/sessions/{sessionId}/revoke")
    @Operation(summary = "Revoquer une session active")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non autorise", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Session introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
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
