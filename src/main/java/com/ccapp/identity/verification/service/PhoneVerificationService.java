package com.ccapp.identity.verification.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.cpass.otp.CPassOtpGateway;
import com.ccapp.cpass.otp.CPassOtpSendResponse;
import com.ccapp.identity.verification.domain.entity.PhoneVerification;
import com.ccapp.identity.verification.domain.enums.PhoneVerificationChannel;
import com.ccapp.identity.verification.domain.enums.PhoneVerificationSource;
import com.ccapp.identity.verification.domain.enums.PhoneVerificationStatus;
import com.ccapp.identity.verification.dto.ConfirmPhoneVerificationRequest;
import com.ccapp.identity.verification.dto.PhoneVerificationResponse;
import com.ccapp.identity.verification.dto.StartPhoneVerificationRequest;
import com.ccapp.identity.verification.repository.PhoneVerificationRepository;
import com.ccapp.identity.service.IdentitySecurityService;
import com.ccapp.progress.event.PhoneVerifiedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

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
    private static final String CPASS_DELEGATED = "CPASS_DELEGATED";

    private final PhoneVerificationRepository phoneVerificationRepository;
    private final UnifiedOtpService unifiedOtpService;
    private final DomainEventPublisher domainEventPublisher;
    private final PhoneVerificationTrustService phoneVerificationTrustService;
    private final IdentitySecurityService identitySecurityService;

    @Nullable
    @Autowired(required = false)
    private CPassOtpGateway cPassOtpGateway;

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

        if (shouldDelegateToCPass(request.channel())) {
            return startVerificationViaCPass(requesterAccountHolderId, request, normalizedPhone, now);
        }

        UnifiedOtpService.OtpMaterial otpMaterial = unifiedOtpService.generateOtp(
                codeLength,
                ttlSeconds,
                "PHONE_VERIFICATION_CODE_LENGTH_INVALID"
        );

        PhoneVerification verification = PhoneVerification.builder()
                .requesterAccountHolderId(requesterAccountHolderId)
                .phoneNumber(normalizedPhone)
                .channel(request.channel())
                .status(PhoneVerificationStatus.PENDING)
                .codeHash(otpMaterial.hash())
                .attemptCount(0)
                .maxAttempts(maxAttempts)
                .sendCount(1)
                .maxSendCount(maxSendCount)
                .lastSentAt(now)
                .resendAvailableAt(now.plusSeconds(resendCooldownSeconds))
                .expiresAt(otpMaterial.expiresAt())
                .build();

        PhoneVerification saved = phoneVerificationRepository.save(verification);
        unifiedOtpService.sendOtp(
                toDeliveryChannel(request.channel()),
                normalizedPhone,
                buildOtpMessage(otpMaterial.code())
        );
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
            boolean verified = cPassOtpGateway.verifyOtp(
                    verification.getCpassReferenceId(),
                    request.code().trim()
            ).verified();
            if (!verified) {
                if (verification.getAttemptCount() >= verification.getMaxAttempts()) {
                    verification.setStatus(PhoneVerificationStatus.FAILED);
                    unifiedOtpService.invalidateOtp("phone-verification:" + verification.getId(), "TOO_MANY_ATTEMPTS");
                }
                phoneVerificationRepository.save(verification);
                throw new BusinessException(PHONE_VERIFICATION_CODE_INVALID);
            }
        } else {
            UnifiedOtpService.VerificationOutcome outcome = unifiedOtpService.verifyOtp(
                    request.code().trim(),
                    verification.getCodeHash(),
                    verification.getExpiresAt(),
                    verification.getAttemptCount(),
                    verification.getMaxAttempts()
            );
            if (outcome != UnifiedOtpService.VerificationOutcome.VERIFIED) {
                if (verification.getAttemptCount() >= verification.getMaxAttempts()) {
                    verification.setStatus(PhoneVerificationStatus.FAILED);
                    unifiedOtpService.invalidateOtp("phone-verification:" + verification.getId(), "TOO_MANY_ATTEMPTS");
                }
                phoneVerificationRepository.save(verification);
                throw new BusinessException(PHONE_VERIFICATION_CODE_INVALID);
            }
        }

        verification.setStatus(PhoneVerificationStatus.VERIFIED);
        verification.setVerifiedAt(OffsetDateTime.now());
        PhoneVerification saved = phoneVerificationRepository.save(verification);
        phoneVerificationTrustService.markPhoneVerified(
                requesterAccountHolderId,
                saved.getPhoneNumber(),
                PhoneVerificationSource.INTERNAL_OTP,
                identitySecurityService.currentRequestMetadata().ipAddress()
        );
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

        UnifiedOtpService.ResendOutcome resendOutcome = unifiedOtpService.evaluateResend(
                verification.getResendAvailableAt(),
                verification.getSendCount(),
                verification.getMaxSendCount()
        );
        if (resendOutcome == UnifiedOtpService.ResendOutcome.TOO_EARLY) {
            throw new BusinessException(PHONE_VERIFICATION_RESEND_TOO_EARLY);
        }
        if (resendOutcome == UnifiedOtpService.ResendOutcome.LIMIT_REACHED) {
            verification.setStatus(PhoneVerificationStatus.FAILED);
            unifiedOtpService.invalidateOtp("phone-verification:" + verification.getId(), "RESEND_LIMIT_REACHED");
            phoneVerificationRepository.save(verification);
            throw new BusinessException(PHONE_VERIFICATION_RESEND_LIMIT_REACHED);
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (shouldDelegateToCPass(verification.getChannel())) {
            CPassOtpSendResponse cpassResp = cPassOtpGateway.sendOtp(verification.getPhoneNumber(), verification.getChannel());
            verification.setCpassReferenceId(cpassResp.referenceId());
            verification.setCodeHash(CPASS_DELEGATED);
            verification.setExpiresAt(cpassResp.expiresAt() != null ? cpassResp.expiresAt() : now.plusSeconds(ttlSeconds));
        } else {
            UnifiedOtpService.OtpMaterial otpMaterial = unifiedOtpService.generateOtp(
                    codeLength,
                    ttlSeconds,
                    "PHONE_VERIFICATION_CODE_LENGTH_INVALID"
            );
            verification.setCodeHash(otpMaterial.hash());
            verification.setExpiresAt(otpMaterial.expiresAt());
            unifiedOtpService.sendOtp(
                    toDeliveryChannel(verification.getChannel()),
                    verification.getPhoneNumber(),
                    buildOtpMessage(otpMaterial.code())
            );
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
            unifiedOtpService.invalidateOtp("phone-verification:" + verification.getId(), "EXPIRED");
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

    private PhoneVerificationResponse startVerificationViaCPass(UUID requesterAccountHolderId,
                                                                StartPhoneVerificationRequest request,
                                                                String normalizedPhone,
                                                                OffsetDateTime now) {
        CPassOtpSendResponse cpassResp = cPassOtpGateway.sendOtp(normalizedPhone, request.channel());
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

    private PhoneVerification getOwnedVerificationOrThrow(UUID requesterAccountHolderId, UUID verificationId) {
        return phoneVerificationRepository.findByIdAndRequesterAccountHolderId(verificationId, requesterAccountHolderId)
                .orElseThrow(() -> new NotFoundException("Phone verification not found"));
    }

    private void failIfExpired(PhoneVerification verification) {
        if (verification.getStatus() == PhoneVerificationStatus.PENDING
                && verification.getExpiresAt().isBefore(OffsetDateTime.now())) {
            verification.setStatus(PhoneVerificationStatus.EXPIRED);
            unifiedOtpService.invalidateOtp("phone-verification:" + verification.getId(), "EXPIRED");
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

    private PhoneVerificationResponse toResponse(PhoneVerification verification) {
        return new PhoneVerificationResponse(
                verification.getId(),
                unifiedOtpService.maskPhone(verification.getPhoneNumber()),
                verification.getChannel(),
                verification.getStatus(),
                verification.getResendAvailableAt(),
                verification.getExpiresAt(),
                verification.getVerifiedAt()
        );
    }

    private boolean shouldDelegateToCPass(PhoneVerificationChannel channel) {
        return cPassOtpGateway != null && cPassOtpGateway.supportsChannel(channel);
    }

    private UnifiedOtpService.OtpDeliveryChannel toDeliveryChannel(PhoneVerificationChannel channel) {
        return channel == PhoneVerificationChannel.WHATSAPP
                ? UnifiedOtpService.OtpDeliveryChannel.WHATSAPP
                : UnifiedOtpService.OtpDeliveryChannel.SMS;
    }

    private String buildOtpMessage(String code) {
        return "Votre code de verification CC App est: " + code;
    }
}
