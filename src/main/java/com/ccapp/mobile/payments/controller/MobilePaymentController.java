package com.ccapp.mobile.payments.controller;

import com.ccapp.common.api.ApiEnvelope;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.mobile.payments.dto.MobilePaymentInitiateRequest;
import com.ccapp.mobile.payments.dto.MobilePaymentInitiateResponse;
import com.ccapp.mobile.payments.dto.MobilePaymentStatusResponse;
import com.ccapp.mobile.payments.service.MobilePaymentService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/mobile/payments")
@RequiredArgsConstructor
@Tag(name = "Mobile Payments", description = "Mobile payment orchestration APIs")
@SecurityRequirement(name = "bearerAuth")
public class MobilePaymentController {

    private final MobilePaymentService mobilePaymentService;

    @PostMapping("/initiate")
    @Operation(summary = "Initiate paid onboarding subscription payment")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = ApiEnvelope.class))),
            @ApiResponse(responseCode = "400", description = "Business validation error", content = @Content(schema = @Schema(implementation = ApiEnvelope.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiEnvelope.class)))
    })
    public ResponseEntity<ApiEnvelope<MobilePaymentInitiateResponse>> initiate(
            @Valid @RequestBody MobilePaymentInitiateRequest request) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(ApiEnvelope.success(
                "Payment initiated",
                mobilePaymentService.initiate(resolveActorUserId(), accountHolderId, request)
        ));
    }

    @GetMapping("/{operationId}/status")
    @Operation(summary = "Get payment operation status")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = ApiEnvelope.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiEnvelope.class))),
            @ApiResponse(responseCode = "404", description = "Payment operation not found", content = @Content(schema = @Schema(implementation = ApiEnvelope.class)))
    })
    public ResponseEntity<ApiEnvelope<MobilePaymentStatusResponse>> getStatus(@PathVariable UUID operationId) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(ApiEnvelope.success(
                "Payment status",
                mobilePaymentService.getStatus(accountHolderId, operationId)
        ));
    }

    private UUID resolveActorUserId() {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        return principal.userId() != null ? principal.userId() : SecurityUtils.currentAccountHolderId();
    }
}
