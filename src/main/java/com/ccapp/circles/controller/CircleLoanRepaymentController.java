package com.ccapp.circles.controller;

import com.ccapp.circles.dto.CircleRepaymentResponse;
import com.ccapp.circles.dto.CreateCircleRepaymentRequest;
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

import java.util.UUID;

@RestController
@RequestMapping("/loans")
@RequiredArgsConstructor
@Validated
@Tag(name = "Circle Repayments", description = "Legacy circle repayment endpoint kept temporarily for compatibility")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
@Deprecated
public class CircleLoanRepaymentController {

    private final CircleFinanceService circleFinanceService;

    /**
     * @deprecated Legacy endpoint kept for compatibility while circle finance is being retired.
     */
    @PostMapping("/{loanId}/repayments")
    @Operation(
            summary = "Enregistrer un remboursement de pret de cycle",
            description = "Legacy endpoint kept temporarily for compatibility. Future canonical repayment path will remain in credit/*.",
            deprecated = true
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Regle metier invalide", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Pret introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    @Deprecated
    public ResponseEntity<CommonResponse<CircleRepaymentResponse>> createRepayment(@PathVariable UUID loanId,
                                                                                    @Valid @RequestBody CreateCircleRepaymentRequest request) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        log.warn("legacy_endpoint_invoked endpointId={} accountHolderId={} loanId={} hasDueDate={}",
                "circle-finance.create-repayment",
                accountHolderId,
                loanId,
                request.dueDate() != null);
        return ResponseEntity.ok(CommonResponse.success(
                "Circle loan repayment recorded",
                circleFinanceService.createRepayment(accountHolderId, loanId, request)
        ));
    }
}
