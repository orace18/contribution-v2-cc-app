package com.ccapp.mobile.onboarding.controller;

import com.ccapp.common.api.ApiEnvelope;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.identity.managedaccounts.service.ManagedAccountReadContextService;
import com.ccapp.mobile.onboarding.dto.MobileOnboardingPlanResponse;
import com.ccapp.mobile.onboarding.dto.MobileOnboardingStatusResponse;
import com.ccapp.mobile.onboarding.dto.MobilePlanSelectionResponse;
import com.ccapp.mobile.onboarding.dto.MobileSponsorSelectionResponse;
import com.ccapp.mobile.onboarding.dto.SelectMobilePlanRequest;
import com.ccapp.mobile.onboarding.dto.SetMobileSponsorRequest;
import com.ccapp.mobile.onboarding.dto.SelectMobileCountryRequest;
import com.ccapp.mobile.onboarding.dto.MobileCountrySelectionResponse;
import com.ccapp.mobile.onboarding.dto.ActivateFreePlanResponse;
import com.ccapp.mobile.onboarding.service.MobileOnboardingJourneyService;
import com.ccapp.mobile.onboarding.service.MobileOnboardingStatusService;
import com.ccapp.mobile.onboarding.service.MobileOnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/mobile/onboarding")
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
@Tag(name = "Mobile Onboarding", description = "Mobile onboarding APIs")
public class MobileOnboardingController {

    private final MobileOnboardingService mobileOnboardingService;
    private final MobileOnboardingJourneyService mobileOnboardingJourneyService;
    private final MobileOnboardingStatusService mobileOnboardingStatusService;
    private final ManagedAccountReadContextService managedAccountReadContextService;

    @GetMapping("/plans")
    @Operation(summary = "List onboarding plans for the requested country catalog")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = ApiEnvelope.class))),
            @ApiResponse(responseCode = "400", description = "Invalid catalog configuration", content = @Content(schema = @Schema(implementation = ApiEnvelope.class)))
    })
    public ResponseEntity<ApiEnvelope<List<MobileOnboardingPlanResponse>>> getPlans(
            @Parameter(description = "Code pays ISO 3166-1 alpha-2 (ex: BJ, CI, CD). Si absent, retourne le catalogue international.", example = "BJ")
            @RequestParam(name = "country", required = false) String country) {
        return ResponseEntity.ok(ApiEnvelope.success(
                "Onboarding plans",
                mobileOnboardingService.getPlans(country)
        ));
    }

    @PostMapping("/select-plan")
    @Operation(summary = "Store the selected onboarding plan")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = ApiEnvelope.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiEnvelope.class))),
            @ApiResponse(responseCode = "404", description = "Subscription plan not found", content = @Content(schema = @Schema(implementation = ApiEnvelope.class)))
    })
    public ResponseEntity<ApiEnvelope<MobilePlanSelectionResponse>> selectPlan(@Valid @RequestBody SelectMobilePlanRequest request) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(ApiEnvelope.success(
                "Plan selected",
                mobileOnboardingService.selectPlan(resolveActorUserId(), accountHolderId, request)
        ));
    }

    @PostMapping("/select-country")
    @Operation(summary = "Select onboarding country")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiEnvelope<MobileCountrySelectionResponse>> selectCountry(
            @Valid @RequestBody SelectMobileCountryRequest request) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(ApiEnvelope.success(
                "Country selected",
                mobileOnboardingJourneyService.selectCountry(
                        resolveActorUserId(),
                        accountHolderId,
                        request.country()
                )
        ));
    }

    @PostMapping("/set-sponsor")
    @Operation(summary = "Set sponsor for onboarding (nullable for no-sponsor edge case)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiEnvelope<MobileSponsorSelectionResponse>> setSponsor(
            @RequestBody(required = false) SetMobileSponsorRequest request) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        UUID sponsorAccountHolderId = request == null ? null : request.sponsorAccountHolderId();
        return ResponseEntity.ok(ApiEnvelope.success(
                "Sponsor set",
                mobileOnboardingJourneyService.setSponsor(
                        resolveActorUserId(),
                        accountHolderId,
                        sponsorAccountHolderId
                )
        ));
    }

    @PostMapping("/activate-free")
    @Operation(summary = "Activate selected FREE plan without payment")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiEnvelope<ActivateFreePlanResponse>> activateFree() {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(ApiEnvelope.success(
                "Free plan activated",
                mobileOnboardingJourneyService.activateFreePlan(resolveActorUserId(), accountHolderId)
        ));
    }

    @GetMapping("/status")
    @Operation(summary = "Get onboarding journey status for mobile orchestration")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiEnvelope<MobileOnboardingStatusResponse>> status(
            @Parameter(description = "Optional target accountHolderId for an authorized managed account")
            @RequestParam(name = "accountHolderId", required = false) UUID requestedAccountHolderId) {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        UUID actorUserId = resolveActorUserId();
        UUID accountHolderId = managedAccountReadContextService.resolveViewTargetAccountHolderId(
                actorUserId,
                principal.accountHolderId() != null ? principal.accountHolderId() : SecurityUtils.currentAccountHolderId(),
                requestedAccountHolderId
        );
        log.info("mobile_onboarding_status_check_started actorUserId={} accountHolderId={}", actorUserId, accountHolderId);
        MobileOnboardingStatusResponse response = mobileOnboardingStatusService.getStatus(actorUserId, accountHolderId);
        log.info("mobile_onboarding_status_check_completed actorUserId={} accountHolderId={} status={}", actorUserId, accountHolderId, response.currentStatus());
        return ResponseEntity.ok(ApiEnvelope.success(
                "Onboarding status",
                response
        ));
    }

    private UUID resolveActorUserId() {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        return principal.userId() != null ? principal.userId() : SecurityUtils.currentAccountHolderId();
    }
}
