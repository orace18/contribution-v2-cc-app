package com.ccapp.identity.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.common.exception.UnauthorizedException;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.domain.entity.User;
import com.ccapp.identity.domain.entity.UserDevice;
import com.ccapp.identity.domain.entity.UserProfile;
import com.ccapp.identity.domain.entity.UserSession;
import com.ccapp.identity.domain.enums.AccountStatus;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.domain.enums.Platform;
import com.ccapp.identity.domain.enums.UserRole;
import com.ccapp.identity.dto.RefreshTokenRequest;
import com.ccapp.identity.dto.RefreshTokenResponse;
import com.ccapp.identity.dto.RegisterDeviceRequest;
import com.ccapp.identity.dto.SyncRequest;
import com.ccapp.identity.dto.SyncResponse;
import com.ccapp.identity.dto.UserDeviceDto;
import com.ccapp.identity.repository.UserDeviceRepository;
import com.ccapp.identity.repository.UserProfileRepository;
import com.ccapp.identity.repository.UserRepository;
import com.ccapp.identity.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdentityDeviceSessionService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final UserSessionRepository userSessionRepository;
    private final AccountHolderService accountHolderService;
    private final AppUserRepository appUserRepository;
    private final RefreshTokenService refreshTokenService;

    @Value("${identity.refresh-token.ttl-hours:720}")
    private long refreshTtlHours;

    @Transactional
    public SyncResponse sync(FirebaseUserClaims claims, SyncRequest request) {
        log.info("Identity sync start firebaseUid={} platform={} deviceIdentifier={}",
                claims.uid(), request.platform(), request.deviceIdentifier());
        User user = userRepository.findByFirebaseUid(claims.uid()).orElseGet(() -> userRepository.save(User.builder()
                .firebaseUid(claims.uid())
                .email(claims.email())
                .role(UserRole.USER)
                .accountStatus(AccountStatus.ACTIVE)
                .kycStatus(KycStatus.NOT_STARTED)
                .build()));

        ensureDelegatedIdentityModel(user);
        ensureAccountActive(user);

        UserDevice device = upsertDevice(user, request.deviceIdentifier(), request.platform(), request.pushToken());
        SessionCreation sessionCreation = createSession(user, device);

        log.info("Identity sync success userId={} sessionId={}", user.getId(), sessionCreation.session().getId());
        return new SyncResponse(
                user.getId(),
                user.getFirebaseUid(),
                user.getEmail(),
                user.getRole(),
                user.getAccountStatus(),
                user.getKycStatus(),
                sessionCreation.session().getId(),
                sessionCreation.rawRefreshToken()
        );
    }

    @Transactional
    public UserDeviceDto registerDevice(UUID userId, RegisterDeviceRequest request) {
        log.info("Identity registerDevice userId={} platform={} deviceIdentifier={}",
                userId, request.platform(), request.deviceIdentifier());
        User user = findUser(userId);
        ensureAccountActive(user);
        UserDevice saved = upsertDevice(user, request.deviceIdentifier(), request.platform(), request.pushToken());
        return new UserDeviceDto(
                saved.getId(),
                saved.getDeviceIdentifier(),
                saved.getPlatform(),
                saved.isBlocked(),
                saved.getLastSeenAt()
        );
    }

    @Transactional
    public void blockDevice(UUID userId, UUID deviceId) {
        log.info("Identity blockDevice userId={} deviceId={}", userId, deviceId);
        UserDevice device = userDeviceRepository.findById(deviceId)
                .orElseThrow(() -> new NotFoundException("Device not found"));
        if (!device.getUser().getId().equals(userId)) {
            log.warn("Identity blockDevice denied userId={} deviceId={} ownerUserId={}",
                    userId, deviceId, device.getUser().getId());
            throw new UnauthorizedException("Cannot block another user device");
        }
        device.setBlocked(true);
        device.setBlockedAt(OffsetDateTime.now());
        userDeviceRepository.save(device);

        List<UserSession> activeSessions = userSessionRepository.findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(userId, OffsetDateTime.now());
        OffsetDateTime revokedAt = OffsetDateTime.now();
        activeSessions.stream()
                .filter(session -> session.getDevice().getId().equals(deviceId))
                .forEach(session -> session.setRevokedAt(revokedAt));
        userSessionRepository.saveAll(activeSessions);
        log.info("Identity blockDevice success userId={} deviceId={} affectedSessions={}",
                userId, deviceId, activeSessions.stream().filter(session -> session.getDevice().getId().equals(deviceId)).count());
    }

    @Transactional
    public RefreshTokenResponse rotateRefreshToken(RefreshTokenRequest request) {
        log.info("Identity rotateRefreshToken start");
        String hash = refreshTokenService.hash(request.refreshToken());
        UserSession currentSession = userSessionRepository.findByRefreshTokenHash(hash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        OffsetDateTime now = OffsetDateTime.now();
        if (currentSession.getRevokedAt() != null || currentSession.getExpiresAt().isBefore(now)) {
            log.warn("Identity rotateRefreshToken denied sessionId={} revokedAt={} expiresAt={}",
                    currentSession.getId(), currentSession.getRevokedAt(), currentSession.getExpiresAt());
            throw new UnauthorizedException("Refresh token expired or revoked");
        }

        User user = currentSession.getUser();
        ensureAccountActive(user);
        if (currentSession.getDevice().isBlocked()) {
            log.warn("Identity rotateRefreshToken denied blocked device sessionId={} deviceId={}",
                    currentSession.getId(), currentSession.getDevice().getId());
            throw new UnauthorizedException("Device is blocked");
        }

        currentSession.setRevokedAt(now);
        userSessionRepository.save(currentSession);

        SessionCreation newSession = createSession(user, currentSession.getDevice());
        log.info("Identity rotateRefreshToken success oldSessionId={} newSessionId={}",
                currentSession.getId(), newSession.session().getId());
        return new RefreshTokenResponse(newSession.session().getId(), newSession.rawRefreshToken());
    }

    @Transactional
    public void revokeSession(UUID userId, UUID sessionId) {
        log.info("Identity revokeSession userId={} sessionId={}", userId, sessionId);
        UserSession session = userSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        if (!session.getUser().getId().equals(userId)) {
            log.warn("Identity revokeSession denied userId={} sessionId={} ownerUserId={}",
                    userId, sessionId, session.getUser().getId());
            throw new UnauthorizedException("Cannot revoke another user session");
        }
        session.setRevokedAt(OffsetDateTime.now());
        userSessionRepository.save(session);
        log.info("Identity revokeSession success userId={} sessionId={}", userId, sessionId);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private UserDevice upsertDevice(User user, String deviceIdentifier, Platform platform, String pushToken) {
        UserDevice device = userDeviceRepository.findByUserIdAndDeviceIdentifierAndDeletedFalse(user.getId(), deviceIdentifier)
                .orElseGet(() -> UserDevice.builder()
                        .user(user)
                        .deviceIdentifier(deviceIdentifier)
                        .deleted(false)
                        .build());
        device.setPlatform(platform);
        device.setLastSeenAt(OffsetDateTime.now());
        String normalizedPushToken = normalizeOptional(pushToken);
        if (normalizedPushToken != null || device.getPushToken() == null) {
            device.setPushToken(normalizedPushToken);
        }
        if (!device.isBlocked()) {
            device.setBlocked(false);
            device.setBlockedAt(null);
        }
        return userDeviceRepository.save(device);
    }

    private SessionCreation createSession(User user, UserDevice device) {
        if (device.isBlocked()) {
            throw new UnauthorizedException("Device is blocked");
        }
        String rawRefreshToken = refreshTokenService.generateToken();
        String refreshTokenHash = refreshTokenService.hash(rawRefreshToken);

        UserSession session = UserSession.builder()
                .user(user)
                .device(device)
                .refreshTokenHash(refreshTokenHash)
                .expiresAt(OffsetDateTime.now().plusHours(refreshTtlHours))
                .build();
        userSessionRepository.save(session);
        return new SessionCreation(session, rawRefreshToken);
    }

    private void ensureAccountActive(User user) {
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException("Account is not active");
        }
    }

    private void ensureDelegatedIdentityModel(User user) {
        UserProfile profile = userProfileRepository.findByUserId(user.getId()).orElse(null);
        Optional<AppUser> existingAppUser = appUserRepository.findById(user.getId())
                .or(() -> appUserRepository.findByFirebaseUid(user.getFirebaseUid()));
        if (existingAppUser.isPresent()) {
            if (!accountHolderService.exists(existingAppUser.get().getAccountHolderId())) {
                accountHolderService.ensureFromLegacyUser(user, profile);
            }
            return;
        }

        accountHolderService.ensureFromLegacyUser(user, profile);
        appUserRepository.save(AppUser.builder()
                .id(user.getId())
                .firebaseUid(user.getFirebaseUid())
                .accountHolderId(user.getId())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .build());
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record SessionCreation(UserSession session, String rawRefreshToken) {
    }
}
