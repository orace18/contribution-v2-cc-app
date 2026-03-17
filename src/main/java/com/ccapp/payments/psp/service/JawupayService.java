package com.ccapp.payments.psp.service;

import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.payments.commission.service.MlmCommissionService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.payments.psp.domain.entity.PspCallbackEvent;
import com.ccapp.payments.psp.dto.JawupayCallbackRequest;
import com.ccapp.payments.psp.repository.PspCallbackEventRepository;
import com.ccapp.payments.subscription.service.MembershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class JawupayService {

    private static final HexFormat HEX = HexFormat.of();

    private final OperationService operationService;
    private final AccountingPostingService accountingPostingService;
    private final MlmCommissionService mlmCommissionService;
    private final MembershipService membershipService;
    private final PspCallbackEventRepository callbackEventRepository;
    private final SimpleRateLimiter simpleRateLimiter;

    @Value("${psp.jawupay.hmac-secret}")
    private String jawupayHmacSecret;

    @Value("${psp.jawupay.replay-window-seconds:300}")
    private long replayWindowSeconds;

    @Value("${psp.jawupay.callback-rate-limit-per-minute:60}")
    private int callbackRateLimitPerMinute;

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
            if (confirmedOperation.getType() == OperationType.SUBSCRIPTION) {
                membershipService.activateForConfirmedOperation(confirmedOperation);
            }
            accountingPostingService.postConfirmedOperation(confirmedOperation.getId());
            mlmCommissionService.calculateForOperation(confirmedOperation);
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
