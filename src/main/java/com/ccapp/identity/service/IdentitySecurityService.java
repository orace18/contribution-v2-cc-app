package com.ccapp.identity.service;

import com.ccapp.common.exception.TooManyRequestsException;
import com.ccapp.common.exception.UnauthorizedException;
import com.ccapp.common.security.ClientRequestMetadata;
import com.ccapp.common.security.ClientRequestMetadataResolver;
import com.ccapp.identity.domain.entity.UserDevice;
import com.ccapp.identity.domain.entity.UserSession;
import com.ccapp.identity.domain.enums.SecurityEventType;
import com.ccapp.identity.domain.enums.SecurityRiskLevel;
import com.ccapp.identity.repository.AuthTokenUsageRepository;
import com.ccapp.identity.repository.LoginAttemptRepository;
import com.ccapp.identity.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdentitySecurityService {

    private final AuthTokenUsageRepository authTokenUsageRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final UserSessionRepository userSessionRepository;
    private final RefreshTokenService refreshTokenService;
    private final ClientRequestMetadataResolver clientRequestMetadataResolver;
    private final IdentitySecurityPersistenceService identitySecurityPersistenceService;

    @Value("${security.mobile-auth.max-login-attempts-per-ip-per-hour:20}")
    private int maxLoginAttemptsPerIpPerHour;

    @Value("${security.mobile-auth.max-login-attempts-per-device-per-hour:10}")
    private int maxLoginAttemptsPerDevicePerHour;

    @Value("${security.mobile-auth.firebase-replay-window-minutes:10}")
    private long firebaseReplayWindowMinutes;

    public ClientRequestMetadata currentRequestMetadata() {
        return clientRequestMetadataResolver.current();
    }

    @Transactional(readOnly = true)
    public void assertLoginAllowed(String ipAddress, String deviceIdentifier) {
        OffsetDateTime threshold = OffsetDateTime.now().minusHours(1);
        if (ipAddress != null && loginAttemptRepository.countByIpAddressAndCreatedAtAfter(ipAddress, threshold) >= maxLoginAttemptsPerIpPerHour) {
            logSecurityEvent(null, SecurityEventType.EXCESSIVE_LOGIN_ATTEMPTS, deviceIdentifier, ipAddress, SecurityRiskLevel.HIGH, null);
            log.warn("mobile_auth_rate_limit_exceeded scope=ip ipAddress={}", ipAddress);
            throw new TooManyRequestsException("TOO_MANY_LOGIN_ATTEMPTS");
        }
        if (deviceIdentifier != null
                && loginAttemptRepository.countByDeviceIdentifierAndCreatedAtAfter(deviceIdentifier, threshold) >= maxLoginAttemptsPerDevicePerHour) {
            logSecurityEvent(null, SecurityEventType.EXCESSIVE_LOGIN_ATTEMPTS, deviceIdentifier, ipAddress, SecurityRiskLevel.HIGH, null);
            log.warn("mobile_auth_rate_limit_exceeded scope=device deviceIdentifier={}", maskDeviceIdentifier(deviceIdentifier));
            throw new TooManyRequestsException("TOO_MANY_LOGIN_ATTEMPTS");
        }
    }

    @Transactional
    public void recordLoginAttempt(String ipAddress, String deviceIdentifier, UUID userId, boolean succeeded) {
        if (deviceIdentifier == null || deviceIdentifier.isBlank() || ipAddress == null || ipAddress.isBlank()) {
            return;
        }
        identitySecurityPersistenceService.saveLoginAttempt(userId, deviceIdentifier.trim(), ipAddress.trim(), succeeded);
    }

    @Transactional(readOnly = true)
    public void assertFreshFirebaseToken(String rawToken, String deviceIdentifier, String ipAddress, String countryCode) {
        String tokenHash = hashToken(rawToken);
        OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(firebaseReplayWindowMinutes);
        if (authTokenUsageRepository.existsByTokenHashAndUsedAtAfter(tokenHash, threshold)) {
            logSecurityEvent(null, SecurityEventType.TOKEN_REPLAY, deviceIdentifier, ipAddress, SecurityRiskLevel.HIGH, countryCode);
            throw new UnauthorizedException("FIREBASE_TOKEN_REPLAY_DETECTED");
        }
    }

    @Transactional
    public void recordFirebaseTokenUsage(String rawToken, UUID userId, String deviceIdentifier) {
        identitySecurityPersistenceService.saveTokenUsage(hashToken(rawToken), userId, normalizeOptional(deviceIdentifier));
    }

    @Transactional
    public void logSecurityEvent(UUID userId,
                                 SecurityEventType eventType,
                                 String deviceIdentifier,
                                 String ipAddress,
                                 SecurityRiskLevel riskLevel,
                                 String countryCode) {
        identitySecurityPersistenceService.saveSecurityEvent(
                userId,
                eventType,
                normalizeOptional(deviceIdentifier),
                normalizeOptional(ipAddress),
                riskLevel,
                normalizeOptional(countryCode)
        );
    }

    @Transactional
    public SecurityRiskLevel assessLoginRisk(UUID userId,
                                             UserDevice device,
                                             boolean newDevice,
                                             boolean ipChanged,
                                             String ipAddress,
                                             String countryCode) {
        boolean highRisk = false;
        if (newDevice && !device.isTrustedDevice()) {
            logSecurityEvent(userId, SecurityEventType.NEW_DEVICE_LOGIN, device.getDeviceIdentifier(), ipAddress, SecurityRiskLevel.HIGH, countryCode);
            highRisk = true;
        }

        if (ipChanged) {
            logSecurityEvent(userId, SecurityEventType.SUSPICIOUS_LOCATION, device.getDeviceIdentifier(), ipAddress, SecurityRiskLevel.HIGH, countryCode);
            highRisk = true;
        }

        if (countryCode != null) {
            UserSession latestSession = userSessionRepository.findTopByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(userId).orElse(null);
            if (latestSession != null
                    && latestSession.getCountryCode() != null
                    && !Objects.equals(latestSession.getCountryCode(), countryCode)) {
                logSecurityEvent(userId, SecurityEventType.SUSPICIOUS_LOCATION, device.getDeviceIdentifier(), ipAddress, SecurityRiskLevel.HIGH, countryCode);
                highRisk = true;
            }
        }

        return highRisk ? SecurityRiskLevel.HIGH : SecurityRiskLevel.LOW;
    }

    @Transactional
    public void handleRefreshTokenReplay(UserSession session, ClientRequestMetadata metadata) {
        String ipAddress = metadata == null ? null : metadata.ipAddress();
        String countryCode = metadata == null ? null : metadata.countryCode();
        logSecurityEvent(session.getUser() == null ? null : session.getUser().getId(),
                SecurityEventType.TOKEN_REPLAY,
                session.getDevice() == null ? null : session.getDevice().getDeviceIdentifier(),
                ipAddress,
                SecurityRiskLevel.HIGH,
                countryCode);
        identitySecurityPersistenceService.revokeActiveSessionsForDevice(
                session.getUser() == null ? null : session.getUser().getId(),
                session.getDevice()
        );
    }

    public String maskDeviceIdentifier(String deviceIdentifier) {
        if (deviceIdentifier == null || deviceIdentifier.isBlank()) {
            return "***";
        }
        String normalized = deviceIdentifier.trim();
        if (normalized.length() <= 6) {
            return "***" + normalized.charAt(normalized.length() - 1);
        }
        return normalized.substring(0, 3) + "***" + normalized.substring(normalized.length() - 3);
    }

    private String hashToken(String rawToken) {
        return refreshTokenService.hash(rawToken == null ? "" : rawToken.trim());
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
