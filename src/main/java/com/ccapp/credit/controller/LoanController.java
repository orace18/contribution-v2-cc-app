package com.ccapp.credit.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.credit.dto.CreateLoanRequest;
import com.ccapp.credit.dto.LoanRepaymentResponse;
import com.ccapp.credit.dto.LoanResponse;
import com.ccapp.credit.dto.RecordLoanDisbursementRequest;
import com.ccapp.credit.dto.RepayLoanRequest;
import com.ccapp.credit.service.LoanService;
import com.ccapp.credit.service.RepaymentService;
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
@RequestMapping("/loans")
@RequiredArgsConstructor
@Tag(name = "Loans", description = "Gestion des prets et remboursements")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
public class LoanController {

    private final LoanService loanService;
    private final RepaymentService repaymentService;

    @PostMapping
    @Operation(
            summary = "Creer une demande de pret",
            description = "Legacy direct loan creation path kept temporarily for compatibility. Canonical seed capital application flow now starts with /credit-applications/seed-capital.",
            deprecated = true
    )
    @Deprecated
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation ou regle metier invalide", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Ressource introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<LoanResponse>> create(@Valid @RequestBody CreateLoanRequest request) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        UUID actorUserId = SecurityUtils.currentPrincipal().userId() != null
                ? SecurityUtils.currentPrincipal().userId()
                : accountHolderId;
        log.warn("legacy_endpoint_invoked endpointId={} actorUserId={} accountHolderId={} circleId={} amount={}",
                "loans.create-direct",
                actorUserId,
                accountHolderId,
                request.circleId(),
                request.amount());

        return ResponseEntity.ok(CommonResponse.success(
                "Loan created (pending funding)",
                loanService.createLoan(accountHolderId, actorUserId, request)
        ));
    }

    @GetMapping("/me")
    @Operation(summary = "Lister mes prets")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account holder introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<List<LoanResponse>>> myLoans() {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success("My loans", loanService.getMyLoans(accountHolderId)));
    }

    @PostMapping("/{loanId}/disbursements")
    @Operation(summary = "Enregistrer un decaissement externe de l'institution")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation ou regle metier invalide", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Pret ou institution introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<LoanResponse>> recordDisbursement(
            @PathVariable UUID loanId,
            @Valid @RequestBody RecordLoanDisbursementRequest request) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        UUID actorUserId = SecurityUtils.currentPrincipal().userId() != null
                ? SecurityUtils.currentPrincipal().userId()
                : accountHolderId;

        return ResponseEntity.ok(CommonResponse.success(
                "Loan disbursement recorded",
                loanService.toResponse(
                        loanService.disburseApprovedLoan(loanId, actorUserId, request.institutionId(), request.referenceId())
                )
        ));
    }

    @PostMapping("/{loanId}/repay")
    @Operation(summary = "Payer une echeance de pret")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation ou regle metier invalide", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Pret introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflit transactionnel", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<LoanRepaymentResponse>> repay(
            @PathVariable UUID loanId,
            @Valid @RequestBody RepayLoanRequest request) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        UUID actorUserId = SecurityUtils.currentPrincipal().userId() != null
                ? SecurityUtils.currentPrincipal().userId()
                : accountHolderId;
        LoanRepaymentResponse response = repaymentService.repay(actorUserId, loanId, request);

        return ResponseEntity.ok(CommonResponse.success(
                response.otpRequired() ? "Loan repayment OTP sent" : "Loan repayment recorded",
                response
        ));
    }
}
