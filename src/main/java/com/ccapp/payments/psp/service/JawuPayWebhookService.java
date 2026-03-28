package com.ccapp.payments.psp.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.repository.OperationRepository;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.payments.psp.client.JawuPayPspClient;
import com.ccapp.payments.psp.dto.JawuPayPaymentResponse;
import com.ccapp.payments.psp.dto.JawuPayPayoutDetailsResponse;
import com.ccapp.payments.psp.dto.JawuPayWebhookRequest;
import com.ccapp.wallet.service.WalletDepositService;
import com.ccapp.wallet.service.WalletWithdrawalService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Processes JawuPay webhook notifications and provides fallback status checks.
 *
 * <p>Security: all webhook calls are validated against a configurable IP allowlist before
 * any business logic runs.
 *
 * <p>Idempotence: operations already in a terminal state (CONFIRMED / FAILED / EXPIRED)
 * are silently ignored so duplicate webhook deliveries are safe.
 *
 * <p>Shared logic: {@link #applyPspStatusToOperation(Operation, String)} is the single
 * method that drives the Operation lifecycle from a raw PSP status string. It is called
 * by both the webhook handler (step 3) and the fallback check (step 4) to avoid any
 * duplication.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JawuPayWebhookService {

    private final OperationService operationService;
    private final OperationRepository operationRepository;
    private final JawuPayPspClient pspClient;
    private final AccountingPostingService accountingPostingService;
    private final WalletDepositService walletDepositService;
    private final WalletWithdrawalService walletWithdrawalService;

    @Value("${psp.jawupay.webhook.allowed-ips:}")
    private String allowedIpsConfig;

    private Set<String> allowedIps;

    @PostConstruct
    void init() {
        allowedIps = Arrays.stream(allowedIpsConfig.split(","))
                .map(String::trim)
                .filter(ip -> !ip.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    // -------------------------------------------------------------------------
    // Step 3 — Webhook handler
    // -------------------------------------------------------------------------

    /**
     * Entry point for {@code POST /psp/jawupay/webhook}.
     *
     * <p>Processing order (as specified):
     * <ol>
     *   <li>Validate the caller IP against the allowlist.</li>
     *   <li>Extract {@code paymentId} or {@code reference} from the payload.</li>
     *   <li>Look up the matching Operation — if not found, log and return.</li>
     *   <li>Delegate to {@link #applyPspStatusToOperation} (shared step 4 logic).</li>
     * </ol>
     */
    @Transactional
    public void processWebhook(JawuPayWebhookRequest request, String remoteIp) {

        // Step 1 — IP allowlist check
        validateIp(remoteIp);

        // Step 2 — resolve the PSP reference (paymentId takes precedence over reference)
        String pspReference = resolvePspReference(request);
        if (pspReference == null) {
            log.warn("Webhook ignored: neither paymentId nor reference present in payload provider={}",
                    request.provider());
            return;
        }

        // Step 3 — look up the Operation
        Optional<Operation> maybeOperation = operationRepository.findByPspReference(pspReference);
        if (maybeOperation.isEmpty()) {
            log.warn("Webhook ignored: no operation found for pspReference={} provider={}",
                    pspReference, request.provider());
            return;
        }

        // Steps 4-7 delegated to the shared method
        applyPspStatusToOperation(maybeOperation.get(), request.status());
    }

    // -------------------------------------------------------------------------
    // Step 4 — Fallback / reconciliation check for payments
    // -------------------------------------------------------------------------

    /**
     * Fetches the current payment status from the PSP and applies it to the Operation.
     * Used when a webhook never arrived, for retries, or during reconciliation.
     *
     * <p>Verifies that the PSP-reported amount and currency match the Operation before
     * applying any state transition.
     */
    @Transactional
    public void checkAndApplyPaymentStatus(String paymentId) {
        Optional<Operation> maybeOperation = operationRepository.findByPspReference(paymentId);
        if (maybeOperation.isEmpty()) {
            log.warn("Fallback check: no operation found for paymentId={}", paymentId);
            return;
        }
        Operation operation = maybeOperation.get();

        JawuPayPaymentResponse payment = pspClient.getPayment(paymentId);

        // Security: verify the PSP-reported amount/currency match our records
        verifyAmountCurrencyMatch(operation, payment.amount(), payment.currency());

        applyPspStatusToOperation(operation, payment.status());
    }

    /**
     * Fetches the current payout status from the PSP and applies it to the Operation.
     * Reuses the same lifecycle logic as the webhook handler.
     */
    @Transactional
    public void checkAndApplyPayoutStatus(String payoutId) {
        Optional<Operation> maybeOperation = operationRepository.findByPspReference(payoutId);
        if (maybeOperation.isEmpty()) {
            log.warn("Payout fallback check: no operation found for payoutId={}", payoutId);
            return;
        }
        Operation operation = maybeOperation.get();

        JawuPayPayoutDetailsResponse payout = pspClient.getPayout(payoutId);

        verifyAmountCurrencyMatch(operation, payout.amount(), payout.currency());

        applyPspStatusToOperation(operation, payout.status());
    }

    // -------------------------------------------------------------------------
    // Shared core logic (steps 4-7 of the webhook spec)
    // -------------------------------------------------------------------------

    /**
     * Applies a raw PSP status string to an Operation following the exact sequence:
     * <ol>
     *   <li>Map the raw PSP value to an internal status.</li>
     *   <li>Enforce idempotence — skip if the operation is already terminal.</li>
     *   <li>If SUCCESS: confirm the Operation, then trigger ledger posting.</li>
     *   <li>If FAILED: mark the Operation as FAILED.</li>
     *   <li>If PENDING: log and take no action.</li>
     * </ol>
     * This method is intentionally package-visible so it can be called from both
     * {@link #processWebhook} and {@link #checkAndApplyPaymentStatus}.
     */
    @Transactional
    public void applyPspStatusToOperation(Operation operation, String rawPspStatus) {
        InternalStatus mapped = mapStatus(rawPspStatus);
        log.info("Applying PSP status: operationId={} rawStatus={} mappedStatus={}",
                operation.getId(), rawPspStatus, mapped);

        // Idempotence: already in a terminal state — nothing to do
        if (isTerminal(operation.getStatus())) {
            log.info("Idempotent: operationId={} already in status={}, ignoring rawStatus={}",
                    operation.getId(), operation.getStatus(), rawPspStatus);
            return;
        }

        switch (mapped) {
            case SUCCESS -> {
                Operation confirmed = operationService.confirmOperation(operation.getId());
                finalizeConfirmedOperation(confirmed);
            }
            case FAILED -> operationService.markFailed(operation.getId());
            case PENDING -> log.info("Operation still pending per PSP: operationId={}", operation.getId());
        }
    }

    // -------------------------------------------------------------------------
    // Post-confirmation dispatch (drives ledger posting per operation type)
    // -------------------------------------------------------------------------

    private void finalizeConfirmedOperation(Operation operation) {
        switch (operation.getType()) {
            case WALLET_DEPOSIT_PSP -> walletDepositService.confirmPspDeposit(operation.getId());
            case WALLET_WITHDRAWAL -> walletWithdrawalService.confirmPspWithdrawal(operation.getId());
            default -> accountingPostingService.postConfirmedOperation(operation.getId());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private enum InternalStatus { SUCCESS, FAILED, PENDING }

    private InternalStatus mapStatus(String raw) {
        if (raw == null) {
            return InternalStatus.PENDING;
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "SUCCESS", "CONFIRMED", "PAID", "COMPLETED" -> InternalStatus.SUCCESS;
            case "FAILED", "DECLINED", "REJECTED", "CANCELLED", "CANCELED" -> InternalStatus.FAILED;
            default -> InternalStatus.PENDING;
        };
    }

    private boolean isTerminal(OperationStatus status) {
        return status == OperationStatus.CONFIRMED
                || status == OperationStatus.FAILED
                || status == OperationStatus.EXPIRED;
    }

    private void validateIp(String remoteIp) {
        if (allowedIps.isEmpty()) {
            // No allowlist configured — permissive (dev/test mode)
            log.debug("IP allowlist empty, allowing request from ip={}", remoteIp);
            return;
        }
        if (!allowedIps.contains(remoteIp)) {
            log.warn("Webhook blocked: ip={} not in allowlist", remoteIp);
            throw new BusinessException("Webhook request from unauthorized IP: " + remoteIp);
        }
    }

    private String resolvePspReference(JawuPayWebhookRequest request) {
        if (request.paymentId() != null && !request.paymentId().isBlank()) {
            return request.paymentId().trim();
        }
        if (request.reference() != null && !request.reference().isBlank()) {
            return request.reference().trim();
        }
        return null;
    }

    /**
     * Verifies that the PSP-reported amount (in minor units, ×100) and currency match
     * the Operation's major-unit amount and currency.
     */
    private void verifyAmountCurrencyMatch(Operation operation, long pspAmountMinorUnits, String pspCurrency) {
        BigDecimal pspAmount = BigDecimal.valueOf(pspAmountMinorUnits).movePointLeft(2);
        if (operation.getAmount().compareTo(pspAmount) != 0) {
            log.error("Amount mismatch: operationId={} expected={} pspReported={}",
                    operation.getId(), operation.getAmount(), pspAmount);
            throw new BusinessException("PSP amount mismatch for operation " + operation.getId());
        }
        if (pspCurrency != null && !operation.getCurrency().equalsIgnoreCase(pspCurrency)) {
            log.error("Currency mismatch: operationId={} expected={} pspReported={}",
                    operation.getId(), operation.getCurrency(), pspCurrency);
            throw new BusinessException("PSP currency mismatch for operation " + operation.getId());
        }
    }
}
