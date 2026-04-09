package com.ccapp.circles.controller;

import com.ccapp.circles.dto.CircleCycleResponse;
import com.ccapp.circles.dto.CircleLoanResponse;
import com.ccapp.circles.dto.CreateCircleCycleRequest;
import com.ccapp.circles.dto.CreateCircleLoanRequest;
import com.ccapp.circles.service.CircleFinanceService;
import com.ccapp.common.api.CommonResponse;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/circles")
@RequiredArgsConstructor
@Validated
@Tag(name = "Circle Finance", description = "Legacy circle finance endpoints kept temporarily for compatibility")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
@Deprecated(since = "2026-04", forRemoval = false)
public class CircleFinanceController {

    private final CircleFinanceService circleFinanceService;

    /**
     * @deprecated Legacy endpoint kept for compatibility while circle finance is being retired.
     */
    @PostMapping("/{circleId}/cycles")
    @Operation(
            summary = "Creer un cycle financier pour un cercle",
            description = "Legacy endpoint kept temporarily for compatibility. Future canonical path will move to circle_savings.",
            deprecated = true
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Regle metier invalide", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cercle introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    @Deprecated
    public ResponseEntity<CommonResponse<CircleCycleResponse>> createCycle(@PathVariable UUID circleId,
                                                                            @Valid @RequestBody CreateCircleCycleRequest request) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        log.warn("legacy_endpoint_invoked endpointId={} accountHolderId={} circleId={} hasContributionAmount={} hasDateRange={}",
                "circle-finance.create-cycle",
                accountHolderId,
                circleId,
                request.contributionAmount() != null,
                request.startDate() != null && request.endDate() != null);
        return ResponseEntity.ok(CommonResponse.success(
                "Circle cycle created",
                circleFinanceService.createCycle(accountHolderId, circleId, request)
        ));
    }

    /**
     * @deprecated Legacy endpoint kept for compatibility while circle finance is being retired.
     */
    @GetMapping("/{circleId}/cycles")
    @Operation(
            summary = "Lister les cycles financiers d'un cercle",
            description = "Legacy endpoint kept temporarily for compatibility. Future canonical path will move to circle_savings projections.",
            deprecated = true
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cercle introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    @Deprecated
    public ResponseEntity<CommonResponse<List<CircleCycleResponse>>> getCycles(@PathVariable UUID circleId) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        log.warn("legacy_endpoint_invoked endpointId={} accountHolderId={} circleId={}",
                "circle-finance.list-cycles",
                accountHolderId,
                circleId);
        return ResponseEntity.ok(CommonResponse.success(
                "Circle cycles",
                circleFinanceService.getCycles(circleId)
        ));
    }

    /**
     * @deprecated Legacy endpoint kept for compatibility while circle finance is being retired.
     */
    @PostMapping("/{circleId}/loans")
    @Operation(
            summary = "Creer et valider un pret de cycle",
            description = "Legacy endpoint kept temporarily for compatibility. Future canonical seed capital will converge on credit/*.",
            deprecated = true
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Regle metier invalide", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cercle introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    @Deprecated
    public ResponseEntity<CommonResponse<CircleLoanResponse>> createLoan(@PathVariable UUID circleId,
                                                                          @Valid @RequestBody CreateCircleLoanRequest request) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        log.warn("legacy_endpoint_invoked endpointId={} accountHolderId={} circleId={} borrowerAccountHolderId={} hasAdvisor={}",
                "circle-finance.create-loan",
                accountHolderId,
                circleId,
                request.borrowerAccountHolderId(),
                request.advisorAccountHolderId() != null);
        return ResponseEntity.ok(CommonResponse.success(
                "Circle loan created",
                circleFinanceService.createLoan(accountHolderId, circleId, request)
        ));
    }
}
