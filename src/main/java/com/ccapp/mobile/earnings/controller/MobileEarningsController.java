package com.ccapp.mobile.earnings.controller;

import com.ccapp.common.api.ApiEnvelope;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.mobile.earnings.dto.MobileEarningHistoryItemResponse;
import com.ccapp.mobile.earnings.dto.MobileEarningsSummaryResponse;
import com.ccapp.mobile.earnings.service.MobileEarningsService;
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
@RequestMapping("/api/mobile/earnings")
@RequiredArgsConstructor
@Tag(name = "Mobile Earnings", description = "Mobile earnings and RMS endpoints")
@SecurityRequirement(name = "bearerAuth")
public class MobileEarningsController {

    private final MobileEarningsService mobileEarningsService;

    @GetMapping("/summary")
    @Operation(summary = "Get RMS earnings summary")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = ApiEnvelope.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiEnvelope.class)))
    })
    public ResponseEntity<ApiEnvelope<MobileEarningsSummaryResponse>> summary() {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(ApiEnvelope.success(
                "Mobile earnings summary",
                mobileEarningsService.getSummary(resolveActorUserId(), accountHolderId)
        ));
    }

    @GetMapping("/history")
    @Operation(summary = "Get earnings history — filter: all (default) | today | week")
    public ResponseEntity<ApiEnvelope<List<MobileEarningHistoryItemResponse>>> history(
            @RequestParam(defaultValue = "all") String filter) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(ApiEnvelope.success(
                "Mobile earnings history",
                mobileEarningsService.getHistory(resolveActorUserId(), accountHolderId, filter)
        ));
    }

    private UUID resolveActorUserId() {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        return principal.userId() != null ? principal.userId() : SecurityUtils.currentAccountHolderId();
    }
}
