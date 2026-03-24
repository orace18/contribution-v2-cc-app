package com.ccapp.mobile.home.controller;

import com.ccapp.common.api.ApiEnvelope;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.mobile.home.dto.MobileHomeDashboardResponse;
import com.ccapp.mobile.home.service.HomeFacadeService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/mobile/home")
@RequiredArgsConstructor
@Tag(name = "Mobile Home", description = "Mobile home aggregation endpoint")
@SecurityRequirement(name = "bearerAuth")
public class MobileHomeController {

    private final HomeFacadeService homeFacadeService;

    @GetMapping
    @Operation(summary = "Aggregate mobile home data")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = ApiEnvelope.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiEnvelope.class))),
            @ApiResponse(responseCode = "404", description = "Account holder not found", content = @Content(schema = @Schema(implementation = ApiEnvelope.class)))
    })
    public ResponseEntity<ApiEnvelope<MobileHomeDashboardResponse>> getHome() {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(ApiEnvelope.success(
                "Mobile home",
                homeFacadeService.getHome(resolveActorUserId(), accountHolderId)
        ));
    }

    private UUID resolveActorUserId() {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        return principal.userId() != null ? principal.userId() : SecurityUtils.currentAccountHolderId();
    }
}
