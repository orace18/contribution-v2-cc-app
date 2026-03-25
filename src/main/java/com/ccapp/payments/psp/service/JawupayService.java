package com.ccapp.payments.psp.service;

import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.payments.commission.service.MlmCommissionService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.repository.OperationRepository;
import com.ccapp.payments.service.PaymentService;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.payments.psp.domain.entity.PspCallbackEvent;
import com.ccapp.payments.psp.dto.JawupayCallbackRequest;
import com.ccapp.payments.psp.repository.PspCallbackEventRepository;
import com.ccapp.payments.subscription.service.MembershipService;
import com.ccapp.mobile.onboarding.service.MobileOnboardingJourneyService;
import com.ccapp.wallet.service.WalletDepositService;
import com.ccapp.wallet.service.WalletWithdrawalService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class JawupayService {

    private static final HexFormat HEX = HexFormat.of();

    private final OperationService operationService;
    private final OperationRepository operationRepository;
    private final PaymentService paymentService;
    private final AccountingPostingService accountingPostingService;
    private final MlmCommissionService mlmCommissionService;
    private final MembershipService membershipService;
    private final WalletDepositService walletDepositService;
    private final WalletWithdrawalService walletWithdrawalService;
    private final PspCallbackEventRepository callbackEventRepository;
    private final SimpleRateLimiter simpleRateLimiter;
    private final MobileOnboardingJourneyService mobileOnboardingJourneyService;

    @Value("${psp.jawupay.hmac-secret}")
    private String jawupayHmacSecret;

    @Value("${psp.jawupay.replay-window-seconds:300}")
    private long replayWindowSeconds;

    @Value("${psp.jawupay.callback-rate-limit-per-minute:60}")
    private int callbackRateLimitPerMinute;

    @Value("${psp.jawupay.pending-expiration-minutes:15}")
    private long pendingExpirationMinutes;

    @Transactional
    public void processCallback(JawupayCallbackRequest request, String signature, String remoteIp) {
        if (!simpleRateLimiter.allow(remoteIp, callbackRateLimitPerMinute)) {
            throw new BusinessException("Rate limit exceeded");
        }

        validateReplay(request);
        validateSignature(request, signature);

        String payloadHash = sha256(canonicalPayload(request));
        callbackEventRepository.save(PspCallbackEvent.builder()
                .nonce(request.nonce())
                .signature(signature)
                .payloadHash(payloadHash)
                .receivedAt(OffsetDateTime.now())
                .build());

        String callbackStatus = request.status().trim().toUpperCase();
        if ("SUCCESS".equals(callbackStatus) || "CONFIRMED".equals(callbackStatus)) {
            Operation confirmedOperation = operationService.confirmByPspReference(
                    request.pspReference(), request.amount(), request.currency());
            finalizeConfirmedOperation(confirmedOperation);
            return;
        }
        if ("FAILED".equals(callbackStatus)) {
            handleFailedOperation(operationService.failByPspReference(request.pspReference()));
            return;
        }
        if ("EXPIRED".equals(callbackStatus) || "TIMEOUT".equals(callbackStatus)) {
            handleExpiredOperation(operationService.expireByPspReference(request.pspReference()));
        }
    }

    @Scheduled(cron = "${psp.jawupay.pending-poll.cron:0 */2 * * * *}", zone = "${mlm.group-bonus.zone:Africa/Porto-Novo}")
    @Transactional
    public void pollPendingPspOperations() {
        List<Operation> pendingOperations = operationRepository
                .findByStatusOrderByCreatedAtAsc(OperationStatus.PENDING_PSP);
        OffsetDateTime now = OffsetDateTime.now();
        for (Operation operation : pendingOperations) {
            if (operation.getPspReference() == null || operation.getPspReference().isBlank()) {
                expireIfTimedOut(operation, now);
                continue;
            }

            String status = paymentService.fetchPaymentStatus(operation.getPspReference()).status();
            String normalizedStatus = status == null ? "PENDING" : status.trim().toUpperCase(Locale.ROOT);
            switch (normalizedStatus) {
                case "SUCCESS", "CONFIRMED" -> finalizeConfirmedOperation(operationService.confirmOperation(operation.getId()));
                case "FAILED" -> handleFailedOperation(operationService.markFailed(operation.getId()));
                case "EXPIRED", "TIMEOUT" -> handleExpiredOperation(operationService.markExpired(operation.getId()));
                default -> expireIfTimedOut(operation, now);
            }
        }
    }

    private void expireIfTimedOut(Operation operation, OffsetDateTime now) {
        if (operation.getCreatedAt() != null
                && operation.getCreatedAt().plusMinutes(pendingExpirationMinutes).isBefore(now)) {
            handleExpiredOperation(operationService.markExpired(operation.getId()));
        }
    }

    private void finalizeConfirmedOperation(Operation operation) {
        switch (operation.getType()) {
            case WALLET_DEPOSIT_PSP -> walletDepositService.confirmPspDeposit(operation.getId());
            case WALLET_WITHDRAWAL -> walletWithdrawalService.confirmPspWithdrawal(operation.getId());
            case SUBSCRIPTION -> {
                membershipService.activateForConfirmedOperation(operation);
                accountingPostingService.postConfirmedOperation(operation.getId());
                mlmCommissionService.calculateForOperation(operation);
                mobileOnboardingJourneyService.onPaidPaymentConfirmed(operation);
            }
            default -> accountingPostingService.postConfirmedOperation(operation.getId());
        }
    }

    private void handleFailedOperation(Operation operation) {
        if (operation.getType() == OperationType.SUBSCRIPTION) {
            membershipService.cancelPendingMembership(operation.getId());
            mobileOnboardingJourneyService.onPaidPaymentFailed(operation);
        }
    }

    private void handleExpiredOperation(Operation operation) {
        if (operation.getType() == OperationType.SUBSCRIPTION) {
            membershipService.cancelPendingMembership(operation.getId());
            mobileOnboardingJourneyService.onPaidPaymentFailed(operation);
        }
    }

    private void validateReplay(JawupayCallbackRequest request) {
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - request.timestamp()) > replayWindowSeconds) {
            throw new BusinessException("Callback timestamp out of replay window");
        }
        if (callbackEventRepository.existsByNonce(request.nonce())) {
            throw new BusinessException("Replay detected");
        }
    }

    private void validateSignature(JawupayCallbackRequest request, String signature) {
        if (signature == null || signature.isBlank()) {
            throw new BusinessException("Missing callback signature");
        }
        String computed = hmacSha256Hex(canonicalPayload(request), jawupayHmacSecret);
        if (!MessageDigest.isEqual(computed.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException("Invalid callback signature");
        }
    }

    private String canonicalPayload(JawupayCallbackRequest request) {
        return String.join("|",
                request.pspReference(),
                request.amount().toPlainString(),
                request.currency(),
                request.status(),
                request.nonce(),
                String.valueOf(request.timestamp()));
    }

    private String hmacSha256Hex(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HEX.formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute HMAC", e);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash payload", e);
        }
    }
}
