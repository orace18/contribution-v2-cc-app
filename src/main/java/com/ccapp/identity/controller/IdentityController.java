package com.ccapp.identity.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.identity.dto.*;
import com.ccapp.identity.dto.UpdateNameRequest;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.domain.entity.User;
import com.ccapp.identity.service.CurrentIdentityService;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/identity")
@RequiredArgsConstructor
@Tag(name = "Identity", description = "Gestion de l'identite, devices et sessions utilisateur")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
public class IdentityController {

    private final IdentityService identityService;
    private final CurrentIdentityService currentIdentityService;

    @PostMapping("/sync")
    @Operation(summary = "Synchroniser l'identite utilisateur")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Erreur de validation/regle metier", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token invalide ou absent", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<SyncResponse>> sync(@Valid @RequestBody SyncRequest request, Authentication authentication) {
        FirebaseUserClaims claims = toClaims(authentication);
        log.info("IdentityController sync firebaseUid={} platform={} deviceIdentifier={}",
                claims.uid(), request.platform(), request.deviceIdentifier());
        return ResponseEntity.ok(CommonResponse.success("Identity synced", identityService.sync(claims, request)));
    }

    @PatchMapping("/country")
    @Operation(summary = "Enregistrer le pays de l'utilisateur")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Pays enregistre"),
            @ApiResponse(responseCode = "400", description = "Code pays invalide ou pays immuable", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<Void> selectCountry(@Valid @RequestBody SelectCountryRequest request) {
        UUID userId = currentIdentityService.resolveCurrentAppUser().getId();
        log.info("IdentityController selectCountry userId={} country={}", userId, request.country());
        identityService.selectCountry(userId, request.country());
        return ResponseEntity.noContent().build();
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
        UUID userId = currentIdentityService.resolveCurrentAppUser().getId();
        log.info("IdentityController onboarding userId={}", userId);
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
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        AppUser appUser = currentIdentityService.resolveCurrentAppUser();
        User user = identityService.findUser(appUser.getId());
        log.debug("IdentityController onboardingStatus principalUserId={} resolvedUserId={} firebaseUid={}",
                principal.userId(), user.getId(), principal.firebaseUid());
        return ResponseEntity.ok(CommonResponse.success("Onboarding status", identityService.getOnboardingStatus(user.getId())));
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
        UUID userId = currentIdentityService.resolveCurrentAppUser().getId();
        log.info("IdentityController onboardingStep1 userId={}", userId);
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
        UUID userId = currentIdentityService.resolveCurrentAppUser().getId();
        log.info("IdentityController onboardingStep2 userId={}", userId);
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
        UUID userId = currentIdentityService.resolveCurrentAppUser().getId();
        log.info("IdentityController onboardingStep3 userId={}", userId);
        return ResponseEntity.ok(CommonResponse.success("Onboarding step 3 updated", identityService.onboardingStep3(userId, request)));
    }

    @PatchMapping("/profile/name")
    @Operation(summary = "Modifier le nom et prenom de l'utilisateur")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Nom mis a jour"),
            @ApiResponse(responseCode = "400", description = "Validation invalide", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<Void> updateName(@Valid @RequestBody UpdateNameRequest request) {
        UUID userId = currentIdentityService.resolveCurrentAppUser().getId();
        log.info("IdentityController updateName userId={}", userId);
        identityService.updateName(userId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Recuperer le profil de l'utilisateur courant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<MeResponse>> me() {
        UUID userId = currentIdentityService.resolveCurrentAppUser().getId();
        log.debug("IdentityController me userId={}", userId);
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
        UUID userId = currentIdentityService.resolveCurrentAppUser().getId();
        log.info("IdentityController registerDevice userId={} platform={} deviceIdentifier={}",
                userId, request.platform(), request.deviceIdentifier());
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
        UUID userId = currentIdentityService.resolveCurrentAppUser().getId();
        log.info("IdentityController blockDevice userId={} deviceId={}", userId, deviceId);
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
        log.info("IdentityController refresh");
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
        UUID userId = currentIdentityService.resolveCurrentAppUser().getId();
        log.info("IdentityController revokeSession userId={} sessionId={}", userId, sessionId);
        identityService.revokeSession(userId, sessionId);
        return ResponseEntity.ok(CommonResponse.success("Session revoked"));
    }

    private FirebaseUserClaims toClaims(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
            throw new IllegalStateException("Invalid authentication context");
        }
        return new FirebaseUserClaims(principal.firebaseUid(), principal.email(), principal.phoneNumber());
    }
}
