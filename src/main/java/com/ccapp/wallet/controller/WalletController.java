package com.ccapp.wallet.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.wallet.dto.WalletAgentDepositRequest;
import com.ccapp.wallet.dto.WalletBalanceResponse;
import com.ccapp.wallet.dto.WalletStatusResponse;
import com.ccapp.wallet.dto.WalletDepositResponse;
import com.ccapp.wallet.dto.WalletPspDepositRequest;
import com.ccapp.wallet.dto.WalletPayRequest;
import com.ccapp.wallet.dto.WalletPayResponse;
import com.ccapp.wallet.dto.WalletPinSetupRequest;
import com.ccapp.wallet.dto.WalletReserveRequest;
import com.ccapp.wallet.dto.WalletReserveResponse;
import com.ccapp.wallet.dto.WalletWithdrawRequest;
import com.ccapp.wallet.dto.WalletWithdrawResponse;
import com.ccapp.wallet.service.WalletBalanceService;
import com.ccapp.wallet.service.WalletDepositService;
import com.ccapp.wallet.service.WalletPaymentService;
import com.ccapp.wallet.service.WalletReservationService;
import com.ccapp.wallet.service.WalletSecurityService;
import com.ccapp.wallet.service.WalletWithdrawalService;
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
    private final WalletSecurityService walletSecurityService;

    @GetMapping("/status")
    @Operation(summary = "Vérifier si le wallet du user connecté est actif")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statut récupéré",
                    content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifié")
    })
    public ResponseEntity<CommonResponse<WalletStatusResponse>> status() {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        return walletBalanceService.findActiveWallet(accountHolderId)
                .map(wallet -> ResponseEntity.ok(CommonResponse.success(
                        "Wallet status",
                        new WalletStatusResponse(wallet.getId(), true, wallet.getPinHash() != null))))
                .orElseGet(() -> ResponseEntity.ok(CommonResponse.success(
                        "Wallet status",
                        new WalletStatusResponse(null, false, false))));
    }

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

    @PostMapping("/pin")
    @Operation(summary = "Set or update the wallet PIN")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PIN updated"),
            @ApiResponse(responseCode = "400", description = "PIN invalid / current PIN invalid"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "404", description = "No active wallet found")
    })
    public ResponseEntity<CommonResponse<Void>> configurePin(
            @Valid @RequestBody WalletPinSetupRequest request) {
        walletSecurityService.configurePin(SecurityUtils.currentAccountHolderId(), request);
        return ResponseEntity.ok(CommonResponse.success("Wallet PIN updated"));
    }

    @PostMapping("/pay")
    @Operation(summary = "Send funds to another wallet by phone number")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment executed"),
            @ApiResponse(responseCode = "400", description = "Insufficient balance / invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "404", description = "Destination wallet not found"),
            @ApiResponse(responseCode = "409", description = "Idempotency conflict")
    })
    public ResponseEntity<CommonResponse<WalletPayResponse>> pay(
            @Valid @RequestBody WalletPayRequest request) {
        var principal = SecurityUtils.currentPrincipal();
        UUID actorUserId = principal.userId() != null ? principal.userId() : SecurityUtils.currentAccountHolderId();
        WalletPayResponse response = walletPaymentService.pay(
                SecurityUtils.currentAccountHolderId(),
                actorUserId,
                request);
        return ResponseEntity.ok(CommonResponse.success(
                response.otpRequired() ? "OTP sent" : "Payment executed",
                response));
    }

    @PostMapping("/deposit/psp")
    @Operation(summary = "Initiate a PSP wallet deposit (returns operationId for PSP redirect)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deposit initiated"),
            @ApiResponse(responseCode = "400", description = "AML limit would be exceeded"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    public ResponseEntity<CommonResponse<WalletDepositResponse>> initiatePspDeposit(
            @Valid @RequestBody WalletPspDepositRequest request) {
        var principal = SecurityUtils.currentPrincipal();
        UUID actorUserId = principal.userId() != null ? principal.userId() : SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success(
                "PSP deposit initiated",
                walletDepositService.initiatePspDeposit(
                        SecurityUtils.currentAccountHolderId(),
                        actorUserId,
                        request)));
    }

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
        UUID actorUserId = principal.userId() != null ? principal.userId() : SecurityUtils.currentAccountHolderId();
        WalletDepositResponse response = walletDepositService.agentDeposit(
                SecurityUtils.currentAccountHolderId(),
                actorUserId,
                request);
        return ResponseEntity.ok(CommonResponse.success(
                response.otpRequired() ? "OTP sent" : "Agent cash-in executed",
                response));
    }

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
        UUID actorUserId = principal.userId() != null ? principal.userId() : SecurityUtils.currentAccountHolderId();
        WalletWithdrawResponse response = walletWithdrawalService.withdraw(
                SecurityUtils.currentAccountHolderId(),
                actorUserId,
                request);
        return ResponseEntity.ok(CommonResponse.success(
                response.otpRequired() ? "OTP sent" : "Withdrawal sent to PSP",
                response));
    }

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
