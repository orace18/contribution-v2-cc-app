package com.ccapp.mobile.network.controller;

import com.ccapp.common.api.ApiEnvelope;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.mobile.network.dto.MobileNetworkEligibilityResponse;
import com.ccapp.mobile.network.dto.MobileNetworkFollowUpResponse;
import com.ccapp.mobile.network.dto.MobileNetworkMembersResponse;
import com.ccapp.mobile.network.dto.MobileNetworkTeamStatsResponse;
import com.ccapp.mobile.network.dto.MobileNetworkTodayActivityResponse;
import com.ccapp.mobile.network.dto.MobileNetworkTreeResponse;
import com.ccapp.mobile.network.dto.MobileNetworkTreeView;
import com.ccapp.mobile.network.service.MobileNetworkService;
import com.ccapp.mobile.onboarding.dto.MobilePlanSelectionResponse;
import com.ccapp.mobile.onboarding.dto.MobileSponsorSelectionResponse;
import com.ccapp.mobile.onboarding.dto.SelectMobilePlanRequest;
import com.ccapp.mobile.onboarding.dto.SetMobileSponsorRequest;
import com.ccapp.mobile.onboarding.service.MobileOnboardingJourneyService;
import com.ccapp.mobile.onboarding.service.MobileOnboardingService;
import org.springframework.web.bind.annotation.PathVariable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/mobile/network")
@RequiredArgsConstructor
@Tag(name = "Mobile Network", description = "Mobile network and team endpoints")
@SecurityRequirement(name = "bearerAuth")
public class MobileNetworkController {

    private final MobileNetworkService mobileNetworkService;
    private final MobileOnboardingJourneyService mobileOnboardingJourneyService;
    private final MobileOnboardingService mobileOnboardingService;

    @GetMapping("/eligibility")
    @Operation(summary = "Check if the current user is eligible to access the MLM network dashboard")
    public ResponseEntity<ApiEnvelope<MobileNetworkEligibilityResponse>> eligibility() {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(ApiEnvelope.success(
                "Network eligibility",
                mobileNetworkService.getEligibility(resolveActorUserId(), accountHolderId)
        ));
    }

    @PostMapping("/parent")
    @Operation(summary = "Set or update the post-onboarding network parent for the current user")
    public ResponseEntity<ApiEnvelope<MobileSponsorSelectionResponse>> setParent(
            @RequestBody(required = false) SetMobileSponsorRequest request) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        UUID sponsorAccountHolderId = request == null ? null : request.sponsorAccountHolderId();
        return ResponseEntity.ok(ApiEnvelope.success(
                "Network parent saved",
                mobileOnboardingJourneyService.setSponsor(
                        resolveActorUserId(),
                        accountHolderId,
                        sponsorAccountHolderId
                )
        ));
    }

    @PostMapping("/membership/select-plan")
    @Operation(summary = "Store the post-onboarding membership plan used for network access")
    public ResponseEntity<ApiEnvelope<MobilePlanSelectionResponse>> selectMembershipPlan(
            @Valid @RequestBody SelectMobilePlanRequest request) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(ApiEnvelope.success(
                "Network membership plan selected",
                mobileOnboardingService.selectPlan(resolveActorUserId(), accountHolderId, request)
        ));
    }

    @GetMapping("/team/stats")
    @Operation(summary = "Get sponsor-network team stats")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = ApiEnvelope.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiEnvelope.class)))
    })
    public ResponseEntity<ApiEnvelope<MobileNetworkTeamStatsResponse>> teamStats() {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(ApiEnvelope.success(
                "Mobile network team stats",
                mobileNetworkService.getTeamStats(resolveActorUserId(), accountHolderId)
        ));
    }

    @GetMapping("/directs")
    @Operation(summary = "List direct sponsored members")
    public ResponseEntity<ApiEnvelope<MobileNetworkMembersResponse>> directs() {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(ApiEnvelope.success(
                "Mobile network directs",
                mobileNetworkService.getDirectMembers(resolveActorUserId(), accountHolderId)
        ));
    }

    @GetMapping("/activity/today")
    @Operation(summary = "Get today's sponsor-network activity")
    public ResponseEntity<ApiEnvelope<MobileNetworkTodayActivityResponse>> todayActivity() {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(ApiEnvelope.success(
                "Mobile network activity",
                mobileNetworkService.getTodayActivity(resolveActorUserId(), accountHolderId)
        ));
    }

    @GetMapping("/followups")
    @Operation(summary = "List members requiring follow-up")
    public ResponseEntity<ApiEnvelope<List<MobileNetworkFollowUpResponse>>> followUps() {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(ApiEnvelope.success(
                "Mobile network follow-ups",
                mobileNetworkService.getFollowUps(resolveActorUserId(), accountHolderId)
        ));
    }

    @GetMapping("/tree")
    @Operation(summary = "Get the network tree for genealogy or placement view")
    public ResponseEntity<ApiEnvelope<MobileNetworkTreeResponse>> tree(
            @RequestParam(defaultValue = "GENEALOGY") MobileNetworkTreeView view) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(ApiEnvelope.success(
                "Mobile network tree",
                mobileNetworkService.getTree(resolveActorUserId(), accountHolderId, view)
        ));
    }

    @GetMapping("/search")
    @Operation(summary = "Search sponsor-network members by name or phone")
    public ResponseEntity<ApiEnvelope<MobileNetworkMembersResponse>> search(@RequestParam("q") String query) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(ApiEnvelope.success(
                "Mobile network search",
                mobileNetworkService.search(resolveActorUserId(), accountHolderId, query)
        ));
    }

    @GetMapping("/member/{memberId}/directs")
    @Operation(summary = "List direct sponsored members of a specific network member")
    public ResponseEntity<ApiEnvelope<MobileNetworkMembersResponse>> memberDirects(
            @PathVariable UUID memberId) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(ApiEnvelope.success(
                "Mobile network member directs",
                mobileNetworkService.getMemberDirects(resolveActorUserId(), accountHolderId, memberId)
        ));
    }

    @GetMapping("/unplaced")
    @Operation(summary = "List sponsor-network members not yet placed in the binary tree")
    public ResponseEntity<ApiEnvelope<MobileNetworkMembersResponse>> unplaced() {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(ApiEnvelope.success(
                "Mobile network unplaced members",
                mobileNetworkService.getUnplacedMembers(resolveActorUserId(), accountHolderId)
        ));
    }

    private UUID resolveActorUserId() {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        return principal.userId() != null ? principal.userId() : SecurityUtils.currentAccountHolderId();
    }
}
