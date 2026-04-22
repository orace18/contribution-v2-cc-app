package com.ccapp.identity.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.common.exception.UnauthorizedException;
import com.ccapp.common.security.ClientRequestMetadata;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountCreatedVia;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
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
import com.ccapp.identity.domain.enums.SecurityRiskLevel;
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
    private final AccountHolderRepository accountHolderRepository;
    private final AppUserRepository appUserRepository;
    private final RefreshTokenService refreshTokenService;
    private final IdentitySecurityService identitySecurityService;
    private final KycStatusConsistencyService kycStatusConsistencyService;

    @Value("${identity.refresh-token.ttl-hours:720}")
    private long refreshTtlHours;

    @Value("${security.mobile-auth.max-devices-per-user:5}")
    private int maxDevicesPerUser;

    @Transactional
    public SyncResponse sync(FirebaseUserClaims claims, SyncRequest request) {
        ClientRequestMetadata metadata = identitySecurityService.currentRequestMetadata();
        log.info("[identity-sync] start firebaseUid={} platform={} deviceIdentifier={} hasPhone={}",
                claims.uid(), request.platform(), request.deviceIdentifier(), claims.phoneNumber() != null);
        User user = ensureUser(claims.uid(), claims.email(), claims.phoneNumber());

        ensureDelegatedIdentityModel(user);
        user.setKycStatus(kycStatusConsistencyService.reconcileAccountHolderStatus(user.getId()));
        ensureAccountActive(user);

        DeviceUpsertResult deviceResult = upsertDevice(
                user,
                request.deviceIdentifier(),
                request.platform(),
                request.pushToken(),
                metadata.ipAddress()
        );
        SecurityRiskLevel riskLevel = identitySecurityService.assessLoginRisk(
                user.getId(),
                deviceResult.device(),
                deviceResult.newDevice(),
                deviceResult.ipChanged(),
                metadata.ipAddress(),
                metadata.countryCode()
        );
        SessionCreation sessionCreation = createSession(user, deviceResult.device(), metadata, riskLevel);

        log.info("[identity-sync] success userId={} sessionId={} deviceNew={}",
                user.getId(), sessionCreation.session().getId(), deviceResult.newDevice());
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
    public User ensureUser(String firebaseUid, String email) {
        return ensureUser(firebaseUid, email, null);
    }

    @Transactional
    public User ensureUser(String firebaseUid, String email, String phoneNumber) {
        String normalizedEmail = normalizeOptional(email);

        // Cas A : firebaseUid connu → réutiliser (idempotent, happy path)
        Optional<User> byUid = userRepository.findByFirebaseUid(firebaseUid);
        if (byUid.isPresent()) {
            log.debug("[identity-sync] resolve=REUSE firebaseUid={} userId={}", firebaseUid, byUid.get().getId());
            User user = byUid.get();
            syncUserClaims(user, new FirebaseUserClaims(firebaseUid, email, phoneNumber));
            return user;
        }

        // Cas B/C : email existant sans ce firebaseUid → rattacher (migration V1 ou réauthentification)
        if (normalizedEmail != null) {
            Optional<User> byEmail = userRepository.findByEmailIgnoreCase(normalizedEmail);
            if (byEmail.isPresent()) {
                User existing = byEmail.get();
                log.warn("[identity-sync] resolve=RELINK userId={} email={} previousFirebaseUid={} newFirebaseUid={} reason=email-match",
                        existing.getId(), normalizedEmail, existing.getFirebaseUid(), firebaseUid);
                existing.setFirebaseUid(firebaseUid);
                return userRepository.save(existing);
            }
        }

        // Cas D/E : lookup par numéro de téléphone vérifié
        String normalizedPhone = normalizeOptional(phoneNumber);
        if (normalizedPhone != null) {
            Optional<AccountHolder> byPhone = accountHolderRepository.findByPhoneNumber(normalizedPhone);
            if (byPhone.isPresent()) {
                AccountHolder foundHolder = byPhone.get();
                List<AppUser> existingAppUsers = appUserRepository.findByAccountHolderId(foundHolder.getId());
                boolean hasAutonomousIdentity = !existingAppUsers.isEmpty();
                if (foundHolder.getCreatedVia() == AccountCreatedVia.MANAGED_ACCOUNT) {
                    // Cas E : managed account — demande confirmation avant de libérer le compte
                    if (!hasAutonomousIdentity) {
                        log.warn("[identity-sync] resolve=MANAGED_ACCOUNT_BLOCKED firebaseUid={} phone={} managedAccountHolderId={}",
                                firebaseUid, normalizedPhone, foundHolder.getId());
                        throw new BusinessException("MANAGED_ACCOUNT_REQUIRES_RELEASE_CONFIRMATION");
                    }
                } else if (!hasAutonomousIdentity) {
                    // Cas D : AccountHolder V1 STANDARD sans identité autonome → rattacher sans créer de doublon AccountHolder
                    log.warn("[identity-sync] resolve=RELINK_V1_PHONE firebaseUid={} phone={} v1AccountHolderId={}",
                            firebaseUid, normalizedPhone, foundHolder.getId());
                    User v1User = userRepository.save(User.builder()
                            .firebaseUid(firebaseUid)
                            .email(normalizedEmail)
                            .role(UserRole.USER)
                            .accountStatus(AccountStatus.ACTIVE)
                            .kycStatus(KycStatus.NOT_STARTED)
                            .build());
                    appUserRepository.save(AppUser.builder()
                            .id(v1User.getId())
                            .firebaseUid(firebaseUid)
                            .accountHolderId(foundHolder.getId())
                            .role(UserRole.USER)
                            .build());
                    return v1User;
                }
            }
        }

        // Cas G : nouvel utilisateur → créer
        log.info("[identity-sync] resolve=CREATE firebaseUid={} email={}", firebaseUid, normalizedEmail);
        return userRepository.save(User.builder()
                .firebaseUid(firebaseUid)
                .email(normalizedEmail)
                .role(UserRole.USER)
                .accountStatus(AccountStatus.ACTIVE)
                .kycStatus(KycStatus.NOT_STARTED)
                .build());
    }

    @Transactional
    public UserDeviceDto registerDevice(UUID userId, RegisterDeviceRequest request) {
        ClientRequestMetadata metadata = identitySecurityService.currentRequestMetadata();
        log.info("Identity registerDevice userId={} platform={} deviceIdentifier={}",
                userId, request.platform(), request.deviceIdentifier());
        User user = findUser(userId);
        ensureAccountActive(user);
        UserDevice saved = upsertDevice(
                user,
                request.deviceIdentifier(),
                request.platform(),
                request.pushToken(),
                metadata.ipAddress()
        ).device();
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
        maybeClearPushTokenIfNoActiveSessions(device);
        log.info("Identity blockDevice success userId={} deviceId={} affectedSessions={}",
                userId, deviceId, activeSessions.stream().filter(session -> session.getDevice().getId().equals(deviceId)).count());
    }

    @Transactional
    public RefreshTokenResponse rotateRefreshToken(RefreshTokenRequest request) {
        ClientRequestMetadata metadata = identitySecurityService.currentRequestMetadata();
        log.info("Identity rotateRefreshToken start");
        String hash = refreshTokenService.hash(request.refreshToken());
        UserSession currentSession = userSessionRepository.findByRefreshTokenHash(hash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        OffsetDateTime now = OffsetDateTime.now();
        if (currentSession.getRevokedAt() != null || currentSession.getExpiresAt().isBefore(now)) {
            identitySecurityService.handleRefreshTokenReplay(currentSession, metadata);
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

        boolean ipChanged = hasIpChanged(currentSession.getDevice(), metadata.ipAddress());
        touchDevice(currentSession.getDevice(), metadata.ipAddress());

        currentSession.setRevokedAt(now);
        userSessionRepository.save(currentSession);

        SecurityRiskLevel riskLevel = identitySecurityService.assessLoginRisk(
                user.getId(),
                currentSession.getDevice(),
                false,
                ipChanged,
                metadata.ipAddress(),
                metadata.countryCode()
        );
        SessionCreation newSession = createSession(user, currentSession.getDevice(), metadata, riskLevel);
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
        maybeClearPushTokenIfNoActiveSessions(session.getDevice());
        log.info("Identity revokeSession success userId={} sessionId={}", userId, sessionId);
    }

    public User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private DeviceUpsertResult upsertDevice(User user,
                                            String deviceIdentifier,
                                            Platform platform,
                                            String pushToken,
                                            String ipAddress) {
        OffsetDateTime now = OffsetDateTime.now();
        UserDevice existingDevice = userDeviceRepository.findByUserIdAndDeviceIdentifierAndDeletedFalse(user.getId(), deviceIdentifier)
                .orElse(null);
        boolean newDevice = existingDevice == null;
        if (newDevice && userDeviceRepository.countByUserIdAndDeletedFalse(user.getId()) >= maxDevicesPerUser) {
            throw new BusinessException("DEVICE_LIMIT_EXCEEDED");
        }

        UserDevice device = existingDevice != null
                ? existingDevice
                : UserDevice.builder()
                .user(user)
                .deviceIdentifier(deviceIdentifier)
                .deleted(false)
                .trustedDevice(userDeviceRepository.countByUserIdAndDeletedFalse(user.getId()) == 0)
                .firstSeenAt(now)
                .build();

        boolean ipChanged = hasIpChanged(device, ipAddress);
        device.setPlatform(platform);
        device.setLastSeenAt(now);
        device.setLastLoginAt(now);
        if (device.getFirstSeenAt() == null) {
            device.setFirstSeenAt(now);
        }
        device.setLastIpAddress(normalizeOptional(ipAddress));
        String normalizedPushToken = normalizeOptional(pushToken);
        if (normalizedPushToken != null) {
            detachPushTokenFromOtherDevices(device, normalizedPushToken);
            device.setPushToken(normalizedPushToken);
        } else if (device.getPushToken() == null) {
            device.setPushToken(null);
        }
        if (!device.isBlocked()) {
            device.setBlocked(false);
            device.setBlockedAt(null);
        }
        return new DeviceUpsertResult(userDeviceRepository.save(device), newDevice, ipChanged);
    }

    private SessionCreation createSession(User user,
                                          UserDevice device,
                                          ClientRequestMetadata metadata,
                                          SecurityRiskLevel riskLevel) {
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
                .ipAddress(metadata == null ? null : normalizeOptional(metadata.ipAddress()))
                .lastSeenAt(OffsetDateTime.now())
                .riskLevel(riskLevel == null ? SecurityRiskLevel.LOW : riskLevel)
                .countryCode(metadata == null ? null : normalizeOptional(metadata.countryCode()))
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

    private void syncUserClaims(User user, FirebaseUserClaims claims) {
        String normalizedEmail = normalizeOptional(claims.email());
        if (normalizedEmail != null && !normalizedEmail.equals(user.getEmail())) {
            Optional<User> conflicting = userRepository.findByEmailIgnoreCase(normalizedEmail);
            if (conflicting.isPresent() && !conflicting.get().getId().equals(user.getId())) {
                log.warn("[identity-sync] email-update-skipped-conflict userId={} currentEmail={} claimedEmail={} conflictingUserId={}",
                        user.getId(), user.getEmail(), normalizedEmail, conflicting.get().getId());
                return;
            }
            log.debug("[identity-sync] email-updated userId={} email={}", user.getId(), normalizedEmail);
            user.setEmail(normalizedEmail);
            userRepository.save(user);
        }
    }

    private void detachPushTokenFromOtherDevices(UserDevice currentDevice, String pushToken) {
        userDeviceRepository.findByDeletedFalseAndPushToken(pushToken).stream()
                .filter(otherDevice -> !otherDevice.getId().equals(currentDevice.getId()))
                .forEach(otherDevice -> {
                    otherDevice.setPushToken(null);
                    userDeviceRepository.save(otherDevice);
                });
    }

    private boolean hasIpChanged(UserDevice device, String ipAddress) {
        String normalizedIp = normalizeOptional(ipAddress);
        return normalizedIp != null
                && device.getLastIpAddress() != null
                && !normalizedIp.equals(device.getLastIpAddress());
    }

    private void touchDevice(UserDevice device, String ipAddress) {
        device.setLastSeenAt(OffsetDateTime.now());
        device.setLastLoginAt(OffsetDateTime.now());
        if (ipAddress != null && !ipAddress.isBlank()) {
            device.setLastIpAddress(ipAddress.trim());
        }
        userDeviceRepository.save(device);
    }

    private void maybeClearPushTokenIfNoActiveSessions(UserDevice device) {
        if (device == null) {
            return;
        }
        boolean hasActiveSessions = userSessionRepository.existsByDeviceIdAndRevokedAtIsNullAndExpiresAtAfter(
                device.getId(),
                OffsetDateTime.now()
        );
        if (!hasActiveSessions && device.getPushToken() != null) {
            device.setPushToken(null);
            userDeviceRepository.save(device);
        }
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record DeviceUpsertResult(UserDevice device, boolean newDevice, boolean ipChanged) {
    }

    private record SessionCreation(UserSession session, String rawRefreshToken) {
    }
}
