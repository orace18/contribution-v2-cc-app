package com.ccapp.identity.service;

import com.ccapp.identity.domain.entity.AuthTokenUsage;
import com.ccapp.identity.domain.entity.LoginAttempt;
import com.ccapp.identity.domain.entity.SecurityEvent;
import com.ccapp.identity.domain.entity.UserDevice;
import com.ccapp.identity.domain.enums.SecurityEventType;
import com.ccapp.identity.domain.enums.SecurityRiskLevel;
import com.ccapp.identity.repository.AuthTokenUsageRepository;
import com.ccapp.identity.repository.LoginAttemptRepository;
import com.ccapp.identity.repository.SecurityEventRepository;
import com.ccapp.identity.repository.UserDeviceRepository;
import com.ccapp.identity.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdentitySecurityPersistenceService {

    private final AuthTokenUsageRepository authTokenUsageRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final SecurityEventRepository securityEventRepository;
    private final UserSessionRepository userSessionRepository;
    private final UserDeviceRepository userDeviceRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveLoginAttempt(UUID userId, String deviceIdentifier, String ipAddress, boolean succeeded) {
        loginAttemptRepository.save(LoginAttempt.builder()
                .userId(userId)
                .deviceIdentifier(deviceIdentifier)
                .ipAddress(ipAddress)
                .succeeded(succeeded)
                .createdAt(OffsetDateTime.now())
                .build());
    }

    @Transactional
    public void saveTokenUsage(String tokenHash, UUID userId, String deviceIdentifier) {
        authTokenUsageRepository.save(AuthTokenUsage.builder()
                .tokenHash(tokenHash)
                .userId(userId)
                .usedAt(OffsetDateTime.now())
                .deviceIdentifier(deviceIdentifier)
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveSecurityEvent(UUID userId,
                                  SecurityEventType eventType,
                                  String deviceIdentifier,
                                  String ipAddress,
                                  SecurityRiskLevel riskLevel,
                                  String countryCode) {
        securityEventRepository.save(SecurityEvent.builder()
                .userId(userId)
                .eventType(eventType)
                .deviceIdentifier(deviceIdentifier)
                .ipAddress(ipAddress)
                .riskLevel(riskLevel)
                .countryCode(countryCode)
                .createdAt(OffsetDateTime.now())
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeActiveSessionsForDevice(UUID userId, UserDevice device) {
        if (userId == null || device == null) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        userSessionRepository.findByUserIdAndDeviceIdAndRevokedAtIsNullAndExpiresAtAfter(userId, device.getId(), now)
                .forEach(activeSession -> activeSession.setRevokedAt(now));
        if (!userSessionRepository.existsByDeviceIdAndRevokedAtIsNullAndExpiresAtAfter(device.getId(), now)) {
            UserDevice managedDevice = userDeviceRepository.findById(device.getId()).orElse(null);
            if (managedDevice != null) {
                managedDevice.setPushToken(null);
                userDeviceRepository.save(managedDevice);
            }
        }
    }
}
