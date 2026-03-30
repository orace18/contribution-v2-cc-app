package com.ccapp.circles.controller;

import com.ccapp.circles.dto.*;
import com.ccapp.circles.service.CircleInviteService;
import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/circles")
@RequiredArgsConstructor
@Validated
@Tag(name = "Circle Invites", description = "Circle invite and QR operations")
@SecurityRequirement(name = "bearerAuth")
public class CircleInviteController {

    private final CircleInviteService circleInviteService;

    @PostMapping("/{circleId}/invites")
    @Operation(summary = "Create an invite link for a circle")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation or business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Circle not found", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<CircleInviteResponse>> createInvite(@PathVariable UUID circleId,
                                                                              @Valid @RequestBody(required = false) CreateCircleInviteRequest request) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        CircleInviteResponse response = circleInviteService.createInvite(accountHolderId, circleId, request);
        return ResponseEntity.ok(CommonResponse.success("Circle invite created", response));
    }

    @GetMapping("/{circleId}/invites/{inviteId}/qr")
    @Operation(summary = "Get invite QR payload")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Invite not found", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<CircleInviteQrResponse>> getQr(@PathVariable UUID circleId,
                                                                         @PathVariable UUID inviteId) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        CircleInviteQrResponse response = circleInviteService.getInviteQr(accountHolderId, circleId, inviteId);
        return ResponseEntity.ok(CommonResponse.success("Circle invite QR payload", response));
    }

    @PostMapping("/{circleId}/invites/{inviteId}/send-whatsapp")
    @Operation(summary = "Send invite by WhatsApp")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation or business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Invite not found", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<SendCircleInviteWhatsAppResponse>> sendInviteWhatsApp(@PathVariable UUID circleId,
                                                                                                 @PathVariable UUID inviteId,
                                                                                                 @Valid @RequestBody SendCircleInviteWhatsAppRequest request) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        SendCircleInviteWhatsAppResponse response = circleInviteService.sendInviteViaWhatsApp(accountHolderId, circleId, inviteId, request);
        return ResponseEntity.ok(CommonResponse.success("Circle invite sent via WhatsApp", response));
    }

    @GetMapping("/invites/code/{code}")
    @Operation(summary = "Resolve a 6-digit invite code — returns circle preview and token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Code expired or invite revoked", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Code not found", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<ResolveInviteCodeResponse>> resolveCode(@PathVariable String code,
                                                                                  HttpServletRequest httpRequest) {
        String clientIp = httpRequest.getRemoteAddr();
        ResolveInviteCodeResponse response = circleInviteService.resolveInviteCode(code, clientIp);
        return ResponseEntity.ok(CommonResponse.success("Invite code resolved", response));
    }

    @PostMapping("/invites/{token}/join")
    @Operation(summary = "Join a circle using invite token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation or business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Invite not found", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflict", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<CircleMemberResponse>> joinWithInvite(@PathVariable String token,
                                                                                @Valid @RequestBody JoinCircleRequest request) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        CircleMemberResponse response = circleInviteService.joinViaInviteToken(accountHolderId, token, request);
        return ResponseEntity.ok(CommonResponse.success("Circle joined", response));
    }
}
