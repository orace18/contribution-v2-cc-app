package com.ccapp.wallet.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.wallet.dto.*;
import com.ccapp.wallet.service.*;
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

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/wallet")
@Tag(name = "Wallet", description = "Wallet balance, payments, deposits, withdrawals and reservations")
@SecurityRequirement(name = "bearerAuth")
public class WalletController {

    private final WalletBalanceService walletBalanceService;
    private final WalletPaymentService walletPaymentService;
    private final WalletDepositService walletDepositService;
    private final WalletWithdrawalService walletWithdrawalService;
    private final WalletReservationService walletReservationService;

    // -------------------------------------------------------------------------
    // Balance
    // -------------------------------------------------------------------------

    @GetMapping("/balance")
    @Operation(summary = "Get wallet balance (derived from ledger)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Balance retrieved",
                    content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "404", description = "No active wallet found")
    })
    public ResponseEntity<CommonResponse<WalletBalanceResponse>> balance(
            @RequestParam String currency) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success(
                "Wallet balance",
                walletBalanceService.getBalance(accountHolderId, currency.toUpperCase())));
    }

    // -------------------------------------------------------------------------
    // Payment (P2P)
    // -------------------------------------------------------------------------

    @PostMapping("/pay")
    @Operation(summary = "Send funds to another wallet by phone number")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment executed",
                    content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Insufficient balance / invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "404", description = "Destination wallet not found"),
            @ApiResponse(responseCode = "409", description = "Idempotency conflict")
    })
    public ResponseEntity<CommonResponse<WalletPayResponse>> pay(
            @Valid @RequestBody WalletPayRequest request) {
        var principal = SecurityUtils.currentPrincipal();
        return ResponseEntity.ok(CommonResponse.success(
                "Payment executed",
                walletPaymentService.pay(
                        SecurityUtils.currentAccountHolderId(),
                        principal.userId(),
                        request)));
    }

    // -------------------------------------------------------------------------
    // Deposit – PSP
    // -------------------------------------------------------------------------

    @PostMapping("/deposit/psp")
    @Operation(summary = "Initiate a PSP wallet deposit (returns operationId for PSP redirect)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deposit initiated"),
            @ApiResponse(responseCode = "400", description = "AML limit would be exceeded"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    public ResponseEntity<CommonResponse<WalletDepositResponse>> initiatePspDeposit(
            @Valid @RequestBody WalletWithdrawRequest request) {
        var principal = SecurityUtils.currentPrincipal();
        return ResponseEntity.ok(CommonResponse.success(
                "PSP deposit initiated",
                walletDepositService.initiatePspDeposit(
                        SecurityUtils.currentAccountHolderId(),
                        principal.userId(),
                        request.amount(),
                        request.currency().toUpperCase(),
                        request.idempotencyKey())));
    }

    // -------------------------------------------------------------------------
    // Deposit – Agent cash-in
    // -------------------------------------------------------------------------

    @PostMapping("/deposit/agent")
    @Operation(summary = "Agent cash-in: debit agent float, credit user wallet")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cash-in executed"),
            @ApiResponse(responseCode = "400", description = "Insufficient agent float / AML limit"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "404", description = "Agent or user wallet not found")
    })
    public ResponseEntity<CommonResponse<WalletDepositResponse>> agentDeposit(
            @Valid @RequestBody WalletAgentDepositRequest request) {
        var principal = SecurityUtils.currentPrincipal();
        return ResponseEntity.ok(CommonResponse.success(
                "Agent cash-in executed",
                walletDepositService.agentDeposit(
                        SecurityUtils.currentAccountHolderId(),
                        principal.userId(),
                        request)));
    }

    // -------------------------------------------------------------------------
    // Withdrawal
    // -------------------------------------------------------------------------

    @PostMapping("/withdraw")
    @Operation(summary = "Withdraw funds to PSP payout (KYC required)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Withdrawal executed"),
            @ApiResponse(responseCode = "400", description = "KYC not verified / insufficient balance"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    public ResponseEntity<CommonResponse<WalletWithdrawResponse>> withdraw(
            @Valid @RequestBody WalletWithdrawRequest request) {
        var principal = SecurityUtils.currentPrincipal();
        return ResponseEntity.ok(CommonResponse.success(
                "Withdrawal executed",
                walletWithdrawalService.withdraw(
                        SecurityUtils.currentAccountHolderId(),
                        principal.userId(),
                        request)));
    }

    // -------------------------------------------------------------------------
    // Reservations
    // -------------------------------------------------------------------------

    @PostMapping("/reserve")
    @Operation(summary = "Reserve funds (reduce available balance without debiting ledger)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Funds reserved"),
            @ApiResponse(responseCode = "400", description = "Insufficient available balance"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    public ResponseEntity<CommonResponse<WalletReserveResponse>> reserve(
            @Valid @RequestBody WalletReserveRequest request) {
        return ResponseEntity.ok(CommonResponse.success(
                "Funds reserved",
                walletReservationService.reserve(
                        SecurityUtils.currentAccountHolderId(), request)));
    }

    @PostMapping("/reservations/{reservationId}/release")
    @Operation(summary = "Release a fund reservation")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reservation released"),
            @ApiResponse(responseCode = "400", description = "Reservation not active"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "404", description = "Reservation not found")
    })
    public ResponseEntity<CommonResponse<Void>> release(
            @PathVariable UUID reservationId) {
        walletReservationService.release(reservationId, SecurityUtils.currentAccountHolderId());
        return ResponseEntity.ok(CommonResponse.success("Reservation released"));
    }
}
