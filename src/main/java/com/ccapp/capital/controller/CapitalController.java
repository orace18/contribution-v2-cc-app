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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/capital")
@RequiredArgsConstructor
@Tag(name = "Capital", description = "Capital funding and pools")
@SecurityRequirement(name = "bearerAuth")
public class CapitalController {

    private final FundService fundService;
    private final CapitalPoolService capitalPoolService;
    private final CapitalFundingService capitalFundingService;

    @PostMapping("/funds")
    @Operation(summary = "Create a fund")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation or business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<FundResponse>> createFund(@Valid @RequestBody CreateFundRequest request) {
        return ResponseEntity.ok(CommonResponse.success("Fund created", fundService.createFund(request)));
    }

    @GetMapping("/funds")
    @Operation(summary = "List funds")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<List<FundResponse>>> listFunds(@RequestParam(required = false) String currency) {
        return ResponseEntity.ok(CommonResponse.success("Funds", fundService.listFunds(currency)));
    }

    @PostMapping("/pools")
    @Operation(summary = "Allocate capital pool for a circle")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation or business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Fund or circle not found", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<CapitalPoolResponse>> allocatePool(
            @Valid @RequestBody AllocateCapitalPoolRequest request) {
        return ResponseEntity.ok(CommonResponse.success("Capital pool allocated", capitalPoolService.allocatePool(request)));
    }

    @GetMapping("/pools")
    @Operation(summary = "List capital pools")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<List<CapitalPoolResponse>>> listPools(@RequestParam(required = false) UUID circleId) {
        return ResponseEntity.ok(CommonResponse.success("Capital pools", capitalPoolService.listPools(circleId)));
    }

    @PostMapping("/loans/{loanId}/fund")
    @Operation(summary = "Fund an approved loan from a capital pool")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation or business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Loan or pool not found", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "409", description = "Loan already funded", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<LoanFundingResponse>> fundLoan(
            @PathVariable UUID loanId,
            @Valid @RequestBody FundLoanRequest request) {
        UUID actorUserId = SecurityUtils.currentPrincipal().userId() != null
                ? SecurityUtils.currentPrincipal().userId()
                : SecurityUtils.currentAccountHolderId();

        return ResponseEntity.ok(CommonResponse.success(
                "Loan funded",
                capitalFundingService.fundLoan(actorUserId, loanId, request.capitalPoolId())
        ));
    }
}
