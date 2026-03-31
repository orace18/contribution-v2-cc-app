package com.ccapp.identity.verification.service;

import com.ccapp.notifications.dispatcher.PhoneMessageGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class UnifiedOtpService {

    private final PhoneMessageGateway phoneMessageGateway;

    public OtpMaterial generateOtp(int length, long ttlSeconds, String invalidLengthCode) {
        if (length < 4 || length > 8) {
            throw new com.ccapp.common.exception.BusinessException(invalidLengthCode);
        }
        int min = (int) Math.pow(10, length - 1);
        int maxExclusive = (int) Math.pow(10, length);
        String code = Integer.toString(ThreadLocalRandom.current().nextInt(min, maxExclusive));
        return new OtpMaterial(code, hashOtp(code), OffsetDateTime.now().plusSeconds(ttlSeconds));
    }

    public String hashOtp(String clearValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(clearValue.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    public VerificationOutcome verifyOtp(String providedCode,
                                         String storedHash,
                                         OffsetDateTime expiresAt,
                                         int attemptCount,
                                         int maxAttempts) {
        if (expiresAt != null && expiresAt.isBefore(OffsetDateTime.now())) {
            return VerificationOutcome.EXPIRED;
        }
        if (storedHash == null || !storedHash.equals(hashOtp(providedCode.trim()))) {
            return attemptCount >= maxAttempts
                    ? VerificationOutcome.TOO_MANY_ATTEMPTS
                    : VerificationOutcome.INVALID;
        }
        return VerificationOutcome.VERIFIED;
    }

    public ResendOutcome evaluateResend(OffsetDateTime resendAvailableAt, int sendCount, int maxSendCount) {
        OffsetDateTime now = OffsetDateTime.now();
        if (resendAvailableAt != null && now.isBefore(resendAvailableAt)) {
            return ResendOutcome.TOO_EARLY;
        }
        if (sendCount >= maxSendCount) {
            return ResendOutcome.LIMIT_REACHED;
        }
        return ResendOutcome.READY;
    }

    public void sendOtp(OtpDeliveryChannel channel, String phoneNumber, String message) {
        if (channel == OtpDeliveryChannel.WHATSAPP) {
            phoneMessageGateway.sendWhatsApp(phoneNumber, message);
            return;
        }
        phoneMessageGateway.sendSms(phoneNumber, message);
    }

    public void invalidateOtp(String otpContext, String reason) {
        log.info("otp_invalidated context={} reason={}", otpContext, reason);
    }

    public String maskPhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "***";
        }
        int keep = Math.min(4, phoneNumber.length());
        return "*".repeat(phoneNumber.length() - keep) + phoneNumber.substring(phoneNumber.length() - keep);
    }

    public enum OtpDeliveryChannel {
        SMS,
        WHATSAPP
    }

    public enum VerificationOutcome {
        VERIFIED,
        INVALID,
        EXPIRED,
        TOO_MANY_ATTEMPTS
    }

    public enum ResendOutcome {
        READY,
        TOO_EARLY,
        LIMIT_REACHED
    }

    public record OtpMaterial(
            String code,
            String hash,
            OffsetDateTime expiresAt
    ) {
    }
}
