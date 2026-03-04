package com.ccapp.identity.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.common.exception.UnauthorizedException;
import com.ccapp.identity.domain.entity.User;
import com.ccapp.identity.domain.entity.UserDevice;
import com.ccapp.identity.domain.entity.UserProfile;
import com.ccapp.identity.domain.entity.UserSession;
import com.ccapp.identity.domain.enums.AccountStatus;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.domain.enums.UserRole;
import com.ccapp.identity.dto.*;
import com.ccapp.identity.mapper.IdentityMapper;
import com.ccapp.identity.repository.UserDeviceRepository;
import com.ccapp.identity.repository.UserProfileRepository;
import com.ccapp.identity.repository.UserRepository;
import com.ccapp.identity.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdentityService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final UserSessionRepository userSessionRepository;
    private final RefreshTokenService refreshTokenService;
    private final IdentityMapper identityMapper;

    @Value("${identity.refresh-token.ttl-hours:720}")
    private long refreshTtlHours;

    @Transactional
    public SyncResponse sync(FirebaseUserClaims claims, SyncRequest request) {
        User user = userRepository.findByFirebaseUid(claims.uid()).orElseGet(() -> userRepository.save(User.builder()
                .firebaseUid(claims.uid())
                .email(claims.email())
                .role(UserRole.USER)
                .accountStatus(AccountStatus.ACTIVE)
                .kycStatus(KycStatus.NOT_STARTED)
                .build()));

        ensureAccountActive(user);

        UserDevice device = upsertDevice(user, request.deviceIdentifier(), request.platform());
        SessionCreation sessionCreation = createSession(user, device);

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
    public MeResponse onboarding(UUID userId, OnboardingRequest request) {
        User user = findUser(userId);
        ensureAccountActive(user);

        UserProfile profile = userProfileRepository.findByUserId(userId)
                .map(existing -> {
                    existing.setFirstName(request.firstName());
                    existing.setLastName(request.lastName());
                    existing.setCountry(request.country());
                    existing.setPhone(request.phone());
                    return existing;
                })
                .orElseGet(() -> UserProfile.builder()
                        .user(user)
                        .firstName(request.firstName())
                        .lastName(request.lastName())
                        .country(request.country())
                        .phone(request.phone())
                        .build());

        userProfileRepository.save(profile);
        if (user.getKycStatus() == KycStatus.NOT_STARTED) {
            user.setKycStatus(KycStatus.PENDING);
            userRepository.save(user);
        }
        return me(userId);
    }

    @Transactional(readOnly = true)
    public MeResponse me(UUID userId) {
        User user = findUser(userId);
        UserProfileDto profile = userProfileRepository.findByUserId(userId)
                .map(identityMapper::toDto)
                .orElse(null);
        List<UserDeviceDto> devices = userDeviceRepository.findByUserIdAndDeletedFalse(userId).stream()
                .map(identityMapper::toDto)
                .toList();
        List<UserSessionDto> sessions = userSessionRepository.findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(userId, OffsetDateTime.now())
                .stream()
                .map(identityMapper::toDto)
                .toList();

        return new MeResponse(
                user.getId(),
                user.getFirebaseUid(),
                user.getEmail(),
                user.getRole(),
                user.getAccountStatus(),
                user.getKycStatus(),
                profile,
                devices,
                sessions
        );
    }

    @Transactional
    public UserDeviceDto registerDevice(UUID userId, RegisterDeviceRequest request) {
        User user = findUser(userId);
        ensureAccountActive(user);
        return identityMapper.toDto(upsertDevice(user, request.deviceIdentifier(), request.platform()));
    }

    @Transactional
    public void blockDevice(UUID userId, UUID deviceId) {
        UserDevice device = userDeviceRepository.findById(deviceId)
                .orElseThrow(() -> new NotFoundException("Device not found"));
        if (!device.getUser().getId().equals(userId)) {
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
    }

    @Transactional
    public RefreshTokenResponse rotateRefreshToken(RefreshTokenRequest request) {
        String hash = refreshTokenService.hash(request.refreshToken());
        UserSession currentSession = userSessionRepository.findByRefreshTokenHash(hash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        OffsetDateTime now = OffsetDateTime.now();
        if (currentSession.getRevokedAt() != null || currentSession.getExpiresAt().isBefore(now)) {
            throw new UnauthorizedException("Refresh token expired or revoked");
        }

        User user = currentSession.getUser();
        ensureAccountActive(user);
        if (currentSession.getDevice().isBlocked()) {
            throw new UnauthorizedException("Device is blocked");
        }

        currentSession.setRevokedAt(now);
        userSessionRepository.save(currentSession);

        SessionCreation newSession = createSession(user, currentSession.getDevice());
        return new RefreshTokenResponse(newSession.session().getId(), newSession.rawRefreshToken());
    }

    @Transactional
    public void revokeSession(UUID userId, UUID sessionId) {
        UserSession session = userSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        if (!session.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Cannot revoke another user session");
        }
        session.setRevokedAt(OffsetDateTime.now());
        userSessionRepository.save(session);
    }

    @Transactional
    public void blockAccount(UUID userId, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        user.setAccountStatus(AccountStatus.BLOCKED);
        user.setBlockedReason(reason);
        userRepository.save(user);

        OffsetDateTime revokedAt = OffsetDateTime.now();
        List<UserSession> activeSessions = userSessionRepository.findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(user.getId(), revokedAt);
        activeSessions.forEach(session -> session.setRevokedAt(revokedAt));
        userSessionRepository.saveAll(activeSessions);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private UserDevice upsertDevice(User user, String deviceIdentifier, com.ccapp.identity.domain.enums.Platform platform) {
        UserDevice device = userDeviceRepository.findByUserIdAndDeviceIdentifierAndDeletedFalse(user.getId(), deviceIdentifier)
                .orElseGet(() -> UserDevice.builder()
                        .user(user)
                        .deviceIdentifier(deviceIdentifier)
                        .deleted(false)
                        .build());
        device.setPlatform(platform);
        device.setLastSeenAt(OffsetDateTime.now());
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

    private record SessionCreation(UserSession session, String rawRefreshToken) {
    }
}
