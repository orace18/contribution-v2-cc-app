package com.ccapp.identity.verification.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.cpass.otp.CPassOtpGateway;
import com.ccapp.cpass.otp.CPassOtpSendResponse;
import com.ccapp.identity.verification.domain.entity.PhoneVerification;
import com.ccapp.identity.verification.domain.enums.PhoneVerificationChannel;
import com.ccapp.identity.verification.domain.enums.PhoneVerificationStatus;
import com.ccapp.identity.verification.dto.ConfirmPhoneVerificationRequest;
import com.ccapp.identity.verification.dto.PhoneVerificationResponse;
import com.ccapp.identity.verification.dto.StartPhoneVerificationRequest;
import com.ccapp.identity.verification.repository.PhoneVerificationRepository;
import com.ccapp.notifications.dispatcher.PhoneMessageGateway;
import com.ccapp.progress.event.PhoneVerifiedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class PhoneVerificationService {

    private static final String PHONE_VERIFICATION_RATE_LIMIT_EXCEEDED = "PHONE_VERIFICATION_RATE_LIMIT_EXCEEDED";
    private static final String PHONE_VERIFICATION_EXPIRED = "PHONE_VERIFICATION_EXPIRED";
    private static final String PHONE_VERIFICATION_CODE_INVALID = "PHONE_VERIFICATION_CODE_INVALID";
    private static final String PHONE_VERIFICATION_TOO_MANY_ATTEMPTS = "PHONE_VERIFICATION_TOO_MANY_ATTEMPTS";
    private static final String PHONE_VERIFICATION_RESEND_TOO_EARLY = "PHONE_VERIFICATION_RESEND_TOO_EARLY";
    private static final String PHONE_VERIFICATION_RESEND_LIMIT_REACHED = "PHONE_VERIFICATION_RESEND_LIMIT_REACHED";
    private static final String PHONE_VERIFICATION_REQUIRED = "PHONE_VERIFICATION_REQUIRED";
    private static final String PHONE_VERIFICATION_PHONE_MISMATCH = "PHONE_VERIFICATION_PHONE_MISMATCH";

    private final PhoneVerificationRepository phoneVerificationRepository;
    private final PhoneMessageGateway phoneMessageGateway;
    private final DomainEventPublisher domainEventPublisher;

    @Nullable
    @Autowired(required = false)
    private CPassOtpGateway cPassOtpGateway;

    private static final String CPASS_DELEGATED = "CPASS_DELEGATED";

    @Value("${identity.phone-verification.code-length:6}")
    private int codeLength;

    @Value("${identity.phone-verification.ttl-seconds:300}")
    private long ttlSeconds;

    @Value("${identity.phone-verification.resend-cooldown-seconds:30}")
    private long resendCooldownSeconds;

    @Value("${identity.phone-verification.max-attempts:5}")
    private int maxAttempts;

    @Value("${identity.phone-verification.max-send-count:3}")
    private int maxSendCount;

    @Value("${identity.phone-verification.start-rate-limit-per-hour:20}")
    private int startRateLimitPerHour;

    @Transactional
    public PhoneVerificationResponse startVerification(UUID requesterAccountHolderId, StartPhoneVerificationRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        long startsInPastHour = phoneVerificationRepository.countByRequesterAccountHolderIdAndCreatedAtAfter(
                requesterAccountHolderId,
                now.minusHours(1)
        );
        if (startsInPastHour >= startRateLimitPerHour) {
            throw new BusinessException(PHONE_VERIFICATION_RATE_LIMIT_EXCEEDED);
        }

        String normalizedPhone = normalizePhone(request.phoneNumber());

        if (cPassOtpGateway != null) {
            return startVerificationViaCPass(requesterAccountHolderId, request, normalizedPhone, now);
        }

        String code = generateCode(codeLength);

        PhoneVerification verification = PhoneVerification.builder()
                .requesterAccountHolderId(requesterAccountHolderId)
                .phoneNumber(normalizedPhone)
                .channel(request.channel())
                .status(PhoneVerificationStatus.PENDING)
                .codeHash(hash(code))
                .attemptCount(0)
                .maxAttempts(maxAttempts)
                .sendCount(1)
                .maxSendCount(maxSendCount)
                .lastSentAt(now)
                .resendAvailableAt(now.plusSeconds(resendCooldownSeconds))
                .expiresAt(now.plusSeconds(ttlSeconds))
                .build();

        PhoneVerification saved = phoneVerificationRepository.save(verification);
        sendCode(request.channel(), normalizedPhone, code);
        return toResponse(saved);
    }

    @Transactional
    public PhoneVerificationResponse confirmVerification(UUID requesterAccountHolderId,
                                                         UUID verificationId,
                                                         ConfirmPhoneVerificationRequest request) {
        PhoneVerification verification = getOwnedVerificationOrThrow(requesterAccountHolderId, verificationId);
        failIfExpired(verification);

        if (verification.getStatus() == PhoneVerificationStatus.VERIFIED) {
            return toResponse(verification);
        }
        if (verification.getStatus() != PhoneVerificationStatus.PENDING) {
            throw new BusinessException(PHONE_VERIFICATION_CODE_INVALID);
        }

        if (verification.getAttemptCount() >= verification.getMaxAttempts()) {
            verification.setStatus(PhoneVerificationStatus.FAILED);
            phoneVerificationRepository.save(verification);
            throw new BusinessException(PHONE_VERIFICATION_TOO_MANY_ATTEMPTS);
        }

        verification.setAttemptCount(verification.getAttemptCount() + 1);

        if (CPASS_DELEGATED.equals(verification.getCodeHash())) {
            // OTP was delegated to CPass — validate remotely
            boolean verified = cPassOtpGateway.verifyOtp(
                    verification.getCpassReferenceId(),
                    request.code().trim()
            ).verified();
            if (!verified) {
                if (verification.getAttemptCount() >= verification.getMaxAttempts()) {
                    verification.setStatus(PhoneVerificationStatus.FAILED);
                }
                phoneVerificationRepository.save(verification);
                throw new BusinessException(PHONE_VERIFICATION_CODE_INVALID);
            }
        } else {
            String providedCodeHash = hash(request.code().trim());
            if (!verification.getCodeHash().equals(providedCodeHash)) {
                if (verification.getAttemptCount() >= verification.getMaxAttempts()) {
                    verification.setStatus(PhoneVerificationStatus.FAILED);
                }
                phoneVerificationRepository.save(verification);
                throw new BusinessException(PHONE_VERIFICATION_CODE_INVALID);
            }
        }

        verification.setStatus(PhoneVerificationStatus.VERIFIED);
        verification.setVerifiedAt(OffsetDateTime.now());
        PhoneVerification saved = phoneVerificationRepository.save(verification);
        domainEventPublisher.publish(PhoneVerifiedEvent.create(
                requesterAccountHolderId,
                saved.getId(),
                saved.getPhoneNumber()
        ));
        return toResponse(saved);
    }

    @Transactional
    public PhoneVerificationResponse resendVerification(UUID requesterAccountHolderId, UUID verificationId) {
        PhoneVerification verification = getOwnedVerificationOrThrow(requesterAccountHolderId, verificationId);
        failIfExpired(verification);

        if (verification.getStatus() != PhoneVerificationStatus.PENDING) {
            throw new BusinessException(PHONE_VERIFICATION_EXPIRED);
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (now.isBefore(verification.getResendAvailableAt())) {
            throw new BusinessException(PHONE_VERIFICATION_RESEND_TOO_EARLY);
        }
        if (verification.getSendCount() >= verification.getMaxSendCount()) {
            verification.setStatus(PhoneVerificationStatus.FAILED);
            phoneVerificationRepository.save(verification);
            throw new BusinessException(PHONE_VERIFICATION_RESEND_LIMIT_REACHED);
        }

        if (cPassOtpGateway != null) {
            CPassOtpSendResponse cpassResp = cPassOtpGateway.sendOtp(verification.getPhoneNumber());
            verification.setCpassReferenceId(cpassResp.referenceId());
            verification.setCodeHash(CPASS_DELEGATED);
            OffsetDateTime expiresAt = cpassResp.expiresAt() != null
                    ? cpassResp.expiresAt()
                    : now.plusSeconds(ttlSeconds);
            verification.setExpiresAt(expiresAt);
        } else {
            String code = generateCode(codeLength);
            verification.setCodeHash(hash(code));
            sendCode(verification.getChannel(), verification.getPhoneNumber(), code);
        }
        verification.setSendCount(verification.getSendCount() + 1);
        verification.setLastSentAt(now);
        verification.setResendAvailableAt(now.plusSeconds(resendCooldownSeconds));

        PhoneVerification saved = phoneVerificationRepository.save(verification);
        return toResponse(saved);
    }

    @Transactional
    public PhoneVerificationResponse getVerification(UUID requesterAccountHolderId, UUID verificationId) {
        PhoneVerification verification = getOwnedVerificationOrThrow(requesterAccountHolderId, verificationId);
        if (verification.getStatus() == PhoneVerificationStatus.PENDING
                && verification.getExpiresAt().isBefore(OffsetDateTime.now())) {
            verification.setStatus(PhoneVerificationStatus.EXPIRED);
            verification = phoneVerificationRepository.save(verification);
        }
        return toResponse(verification);
    }

    @Transactional(readOnly = true)
    public void assertVerifiedForPhone(UUID requesterAccountHolderId, String phoneNumber, UUID verificationId) {
        if (verificationId == null) {
            throw new BusinessException(PHONE_VERIFICATION_REQUIRED);
        }
        PhoneVerification verification = getOwnedVerificationOrThrow(requesterAccountHolderId, verificationId);
        if (verification.getStatus() != PhoneVerificationStatus.VERIFIED) {
            throw new BusinessException(PHONE_VERIFICATION_REQUIRED);
        }
        if (!normalizePhone(phoneNumber).equals(normalizePhone(verification.getPhoneNumber()))) {
            throw new BusinessException(PHONE_VERIFICATION_PHONE_MISMATCH);
        }
    }

    private PhoneVerificationResponse startVerificationViaCPass(
            UUID requesterAccountHolderId,
            StartPhoneVerificationRequest request,
            String normalizedPhone,
            OffsetDateTime now
    ) {
        CPassOtpSendResponse cpassResp = cPassOtpGateway.sendOtp(normalizedPhone);
        OffsetDateTime expiresAt = cpassResp.expiresAt() != null
                ? cpassResp.expiresAt()
                : now.plusSeconds(ttlSeconds);

        PhoneVerification verification = PhoneVerification.builder()
                .requesterAccountHolderId(requesterAccountHolderId)
                .phoneNumber(normalizedPhone)
                .channel(request.channel())
                .status(PhoneVerificationStatus.PENDING)
                .codeHash(CPASS_DELEGATED)
                .cpassReferenceId(cpassResp.referenceId())
                .attemptCount(0)
                .maxAttempts(maxAttempts)
                .sendCount(1)
                .maxSendCount(maxSendCount)
                .lastSentAt(now)
                .resendAvailableAt(now.plusSeconds(resendCooldownSeconds))
                .expiresAt(expiresAt)
                .build();

        return toResponse(phoneVerificationRepository.save(verification));
    }

    private void sendCode(PhoneVerificationChannel channel, String phoneNumber, String code) {
        String message = "Votre code de verification CC App est: " + code;
        if (channel == PhoneVerificationChannel.WHATSAPP) {
            phoneMessageGateway.sendWhatsApp(phoneNumber, message);
            return;
        }
        phoneMessageGateway.sendSms(phoneNumber, message);
    }

    private PhoneVerification getOwnedVerificationOrThrow(UUID requesterAccountHolderId, UUID verificationId) {
        return phoneVerificationRepository.findByIdAndRequesterAccountHolderId(verificationId, requesterAccountHolderId)
                .orElseThrow(() -> new NotFoundException("Phone verification not found"));
    }

    private void failIfExpired(PhoneVerification verification) {
        if (verification.getStatus() == PhoneVerificationStatus.PENDING
                && verification.getExpiresAt().isBefore(OffsetDateTime.now())) {
            verification.setStatus(PhoneVerificationStatus.EXPIRED);
            phoneVerificationRepository.save(verification);
            throw new BusinessException(PHONE_VERIFICATION_EXPIRED);
        }
    }

    private String normalizePhone(String phoneNumber) {
        String normalized = phoneNumber == null ? "" : phoneNumber.trim();
        if (normalized.isEmpty()) {
            throw new BusinessException("PHONE_NUMBER_INVALID");
        }
        return normalized;
    }

    private String generateCode(int length) {
        if (length < 4 || length > 8) {
            throw new BusinessException("PHONE_VERIFICATION_CODE_LENGTH_INVALID");
        }
        int min = (int) Math.pow(10, length - 1);
        int maxExclusive = (int) Math.pow(10, length);
        int value = ThreadLocalRandom.current().nextInt(min, maxExclusive);
        return Integer.toString(value);
    }

    private String hash(String clearValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(clearValue.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private PhoneVerificationResponse toResponse(PhoneVerification verification) {
        return new PhoneVerificationResponse(
                verification.getId(),
                maskPhone(verification.getPhoneNumber()),
                verification.getChannel(),
                verification.getStatus(),
                verification.getResendAvailableAt(),
                verification.getExpiresAt(),
                verification.getVerifiedAt()
        );
    }

    private String maskPhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "***";
        }
        int keep = Math.min(4, phoneNumber.length());
        return "*".repeat(phoneNumber.length() - keep) + phoneNumber.substring(phoneNumber.length() - keep);
    }
}
