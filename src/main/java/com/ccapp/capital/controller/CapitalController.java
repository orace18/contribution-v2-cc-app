package com.ccapp.capital.controller;

import com.ccapp.capital.dto.*;
import com.ccapp.capital.service.CapitalFundingService;
import com.ccapp.capital.service.CapitalPoolService;
import com.ccapp.capital.service.FundService;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/capital")
@RequiredArgsConstructor
@Tag(name = "Capital", description = "Legacy capital funding and pools kept temporarily for compatibility")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
@Deprecated(since = "2026-04", forRemoval = false)
public class CapitalController {

    private final FundService fundService;
    private final CapitalPoolService capitalPoolService;
    private final CapitalFundingService capitalFundingService;

    @PostMapping("/funds")
    @Operation(summary = "Create a fund", deprecated = true)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation or business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    @Deprecated
    public ResponseEntity<CommonResponse<FundResponse>> createFund(@Valid @RequestBody CreateFundRequest request) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        log.warn("legacy_endpoint_invoked endpointId={} accountHolderId={} currency={} provider={}",
                "capital.create-fund",
                accountHolderId,
                request.currency(),
                request.provider());
        return ResponseEntity.ok(CommonResponse.success("Fund created", fundService.createFund(request)));
    }

    @GetMapping("/funds")
    @Operation(summary = "List funds", deprecated = true)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    @Deprecated
    public ResponseEntity<CommonResponse<List<FundResponse>>> listFunds(@RequestParam(required = false) String currency) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        log.warn("legacy_endpoint_invoked endpointId={} accountHolderId={} currencyFilterPresent={}",
                "capital.list-funds",
                accountHolderId,
                currency != null && !currency.isBlank());
        return ResponseEntity.ok(CommonResponse.success("Funds", fundService.listFunds(currency)));
    }

    @PostMapping("/pools")
    @Operation(summary = "Allocate capital pool for a circle", deprecated = true)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation or business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Fund or circle not found", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    @Deprecated
    public ResponseEntity<CommonResponse<CapitalPoolResponse>> allocatePool(
            @Valid @RequestBody AllocateCapitalPoolRequest request) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        log.warn("legacy_endpoint_invoked endpointId={} accountHolderId={} circleId={} fundId={} hasAmount={}",
                "capital.allocate-pool",
                accountHolderId,
                request.circleId(),
                request.fundId(),
                request.allocatedAmount() != null);
        return ResponseEntity.ok(CommonResponse.success("Capital pool allocated", capitalPoolService.allocatePool(accountHolderId, request)));
    }

    @GetMapping("/pools")
    @Operation(summary = "List capital pools", deprecated = true)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    @Deprecated
    public ResponseEntity<CommonResponse<List<CapitalPoolResponse>>> listPools(@RequestParam(required = false) UUID circleId) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        log.warn("legacy_endpoint_invoked endpointId={} accountHolderId={} circleId={}",
                "capital.list-pools",
                accountHolderId,
                circleId);
        return ResponseEntity.ok(CommonResponse.success("Capital pools", capitalPoolService.listPools(accountHolderId, circleId)));
    }

    @PostMapping("/loans/{loanId}/fund")
    @Operation(summary = "Fund an approved loan from a capital pool", deprecated = true)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation or business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Loan or pool not found", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "409", description = "Loan already funded", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    @Deprecated
    public ResponseEntity<CommonResponse<LoanFundingResponse>> fundLoan(
            @PathVariable UUID loanId,
            @Valid @RequestBody FundLoanRequest request) {
        UUID actorUserId = SecurityUtils.currentPrincipal().userId() != null
                ? SecurityUtils.currentPrincipal().userId()
                : SecurityUtils.currentAccountHolderId();
        log.warn("legacy_endpoint_invoked endpointId={} actorUserId={} loanId={} capitalPoolId={}",
                "capital.fund-loan",
                actorUserId,
                loanId,
                request.capitalPoolId());

        return ResponseEntity.ok(CommonResponse.success(
                "Loan funded",
                capitalFundingService.fundLoan(actorUserId, loanId, request.capitalPoolId())
        ));
    }
}
