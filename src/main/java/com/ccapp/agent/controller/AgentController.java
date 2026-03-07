package com.ccapp.agent.controller;

import com.ccapp.agent.dto.*;
import com.ccapp.agent.service.AgentService;
import com.ccapp.agent.service.AssistedSessionService;
import com.ccapp.agent.service.OfflineTransactionService;
import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.common.security.SecurityUtils;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/agents")
@RequiredArgsConstructor
@Tag(name = "Agents", description = "Assisted access and offline operations")
@SecurityRequirement(name = "bearerAuth")
public class AgentController {

    private final AgentService agentService;
    private final AssistedSessionService assistedSessionService;
    private final OfflineTransactionService offlineTransactionService;

    @PostMapping
    @Operation(summary = "Create an agent profile")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation or business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "App user or circle not found", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<AgentProfileResponse>> createAgent(@Valid @RequestBody CreateAgentRequest request) {
        return ResponseEntity.ok(CommonResponse.success("Agent profile created", agentService.createAgent(request)));
    }

    @GetMapping
    @Operation(summary = "List current user agent profiles")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<List<AgentProfileResponse>>> listAgents() {
        UUID actorUserId = resolveActorUserId();
        return ResponseEntity.ok(CommonResponse.success("Agent profiles", agentService.listAgents(actorUserId)));
    }

    @PostMapping("/sessions")
    @Operation(summary = "Log an assisted session for audit")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation or business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account holder not found", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<AssistedSessionResponse>> createSession(
            @Valid @RequestBody CreateAssistedSessionRequest request) {
        UUID actorUserId = resolveActorUserId();
        return ResponseEntity.ok(CommonResponse.success(
                "Assisted session created",
                assistedSessionService.createSession(actorUserId, request)
        ));
    }

    @PostMapping("/offline-transactions")
    @Operation(summary = "Create offline transaction while connectivity is unavailable")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation or business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account holder not found", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<OfflineTransactionResponse>> createOfflineTransaction(
            @Valid @RequestBody CreateOfflineTransactionRequest request) {
        UUID actorUserId = resolveActorUserId();
        return ResponseEntity.ok(CommonResponse.success(
                "Offline transaction created",
                offlineTransactionService.createOfflineTransaction(actorUserId, request)
        ));
    }

    @PostMapping("/offline-transactions/sync")
    @Operation(summary = "Synchronize offline transactions after connectivity restoration")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation or business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<SyncOfflineTransactionsResponse>> syncOfflineTransactions(
            @RequestBody(required = false) SyncOfflineTransactionsRequest request) {
        UUID actorUserId = resolveActorUserId();
        return ResponseEntity.ok(CommonResponse.success(
                "Offline transactions synchronized",
                offlineTransactionService.sync(actorUserId, request)
        ));
    }

    private UUID resolveActorUserId() {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        return principal.userId() != null ? principal.userId() : SecurityUtils.currentAccountHolderId();
    }
}
