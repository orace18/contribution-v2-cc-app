package com.ccapp.mlm.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.mlm.dto.MlmHierarchyMemberResponse;
import com.ccapp.mlm.dto.MlmPlacementRequest;
import com.ccapp.mlm.dto.MlmPlacementResponse;
import com.ccapp.mlm.dto.MlmTeamSizeResponse;
import com.ccapp.mlm.service.MLMPlacementService;
import com.ccapp.mlm.service.MLMQueryService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/mlm")
@RequiredArgsConstructor
@Tag(name = "MLM", description = "MLM placement and hierarchy queries")
@SecurityRequirement(name = "bearerAuth")
public class MLMController {

    private final MLMPlacementService mlmPlacementService;
    private final MLMQueryService mlmQueryService;

    @PostMapping("/placements")
    @Operation(summary = "Place an account holder under a sponsor respecting available legs")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation or business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account holder or sponsor not found", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "409", description = "Account holder already placed", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<MlmPlacementResponse>> place(@Valid @RequestBody MlmPlacementRequest request) {
        return ResponseEntity.ok(CommonResponse.success(
                "MLM placement completed",
                mlmPlacementService.placeAccountHolder(
                        resolveActorUserId(),
                        request.accountHolderId(),
                        request.sponsorAccountHolderId()
                )
        ));
    }

    @GetMapping("/{accountHolderId}/upline")
    @Operation(summary = "Get upline for account holder")
    public ResponseEntity<CommonResponse<List<MlmHierarchyMemberResponse>>> getUpline(@PathVariable UUID accountHolderId) {
        return ResponseEntity.ok(CommonResponse.success(
                "MLM upline",
                mlmQueryService.getUpline(resolveActorUserId(), accountHolderId)
        ));
    }

    @GetMapping("/{accountHolderId}/downline")
    @Operation(summary = "Get downline for account holder")
    public ResponseEntity<CommonResponse<List<MlmHierarchyMemberResponse>>> getDownline(@PathVariable UUID accountHolderId) {
        return ResponseEntity.ok(CommonResponse.success(
                "MLM downline",
                mlmQueryService.getDownline(resolveActorUserId(), accountHolderId)
        ));
    }

    @GetMapping("/{accountHolderId}/team-size")
    @Operation(summary = "Get team size for account holder")
    public ResponseEntity<CommonResponse<MlmTeamSizeResponse>> getTeamSize(@PathVariable UUID accountHolderId) {
        return ResponseEntity.ok(CommonResponse.success(
                "MLM team size",
                mlmQueryService.getTeamSize(resolveActorUserId(), accountHolderId)
        ));
    }

    @GetMapping("/{accountHolderId}/levels/{level}/members")
    @Operation(summary = "Get MLM members at a given level")
    public ResponseEntity<CommonResponse<List<MlmHierarchyMemberResponse>>> getLevelMembers(@PathVariable UUID accountHolderId,
                                                                                             @PathVariable int level) {
        return ResponseEntity.ok(CommonResponse.success(
                "MLM level members",
                mlmQueryService.getLevelMembers(resolveActorUserId(), accountHolderId, level)
        ));
    }

    private UUID resolveActorUserId() {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        return principal.userId() != null ? principal.userId() : SecurityUtils.currentAccountHolderId();
    }
}
