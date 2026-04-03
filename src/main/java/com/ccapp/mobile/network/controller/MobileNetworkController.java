package com.ccapp.mobile.network.controller;

import com.ccapp.common.api.ApiEnvelope;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.mobile.network.dto.MobileNetworkFollowUpResponse;
import com.ccapp.mobile.network.dto.MobileNetworkMembersResponse;
import com.ccapp.mobile.network.dto.MobileNetworkTeamStatsResponse;
import com.ccapp.mobile.network.dto.MobileNetworkTodayActivityResponse;
import com.ccapp.mobile.network.dto.MobileNetworkTreeResponse;
import com.ccapp.mobile.network.dto.MobileNetworkTreeView;
import com.ccapp.mobile.network.service.MobileNetworkService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/mobile/network")
@RequiredArgsConstructor
@Tag(name = "Mobile Network", description = "Mobile network and team endpoints")
@SecurityRequirement(name = "bearerAuth")
public class MobileNetworkController {

    private final MobileNetworkService mobileNetworkService;

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

    private UUID resolveActorUserId() {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        return principal.userId() != null ? principal.userId() : SecurityUtils.currentAccountHolderId();
    }
}
