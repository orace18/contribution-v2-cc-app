package com.ccapp.identity.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.common.exception.UnauthorizedException;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.domain.entity.User;
import com.ccapp.identity.domain.entity.UserDevice;
import com.ccapp.identity.domain.entity.UserProfile;
import com.ccapp.identity.domain.entity.UserSession;
import com.ccapp.identity.domain.enums.AccountStatus;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.domain.enums.OnboardingStep;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdentityService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final UserSessionRepository userSessionRepository;
    private final AccountHolderService accountHolderService;
    private final AppUserRepository appUserRepository;
    private final RefreshTokenService refreshTokenService;
    private final OnboardingCatalogService onboardingCatalogService;
    private final IdentityMapper identityMapper;

    @Value("${identity.refresh-token.ttl-hours:720}")
    private long refreshTtlHours;

    private static final int ONBOARDING_TOTAL_STEPS = 3;
    private static final String DEFAULT_COUNTRY_CODE = "INT";

    @Transactional
    public SyncResponse sync(FirebaseUserClaims claims, SyncRequest request) {
        User user = userRepository.findByFirebaseUid(claims.uid()).orElseGet(() -> userRepository.save(User.builder()
                .firebaseUid(claims.uid())
                .email(claims.email())
                .role(UserRole.USER)
                .accountStatus(AccountStatus.ACTIVE)
                .kycStatus(KycStatus.NOT_STARTED)
                .build()));

        ensureDelegatedIdentityModel(user);
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
        onboardingStep1(userId, new OnboardingStep1Request(
                request.firstName(),
                request.lastName(),
                request.country(),
                request.phone()
        ));
        return me(userId);
    }

    @Transactional
    public OnboardingStatusResponse getOnboardingStatus(UUID userId) {
        User user = findUser(userId);
        ensureAccountActive(user);
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .map(existing -> {
                    updateOnboardingState(existing);
                    return userProfileRepository.save(existing);
                })
                .orElse(null);
        return buildOnboardingStatus(user, profile);
    }

    @Transactional
    public OnboardingStatusResponse onboardingStep1(UUID userId, OnboardingStep1Request request) {
        User user = findUser(userId);
        ensureAccountActive(user);

        AccountHolder accountHolder = accountHolderService.updateProfile(
                user.getId(),
                request.firstName().trim(),
                request.lastName().trim(),
                request.phone().trim(),
                request.country().trim().toUpperCase(Locale.ROOT)
        );
        String canonicalCountryCode = accountHolder.getCountryCode();

        UserProfile profile = getOrCreateProfile(user);
        profile.setFirstName(request.firstName().trim());
        profile.setLastName(request.lastName().trim());
        profile.setCountry(canonicalCountryCode);
        profile.setPhone(request.phone().trim());
        updateOnboardingState(profile);

        UserProfile saved = userProfileRepository.save(profile);
        if (user.getKycStatus() == KycStatus.NOT_STARTED) {
            user.setKycStatus(KycStatus.PENDING);
            userRepository.save(user);
        }

        return buildOnboardingStatus(user, saved);
    }

    @Transactional
    public OnboardingStatusResponse onboardingStep2(UUID userId, OnboardingStep2Request request) {
        User user = findUser(userId);
        ensureAccountActive(user);

        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("Onboarding step 1 must be completed first"));

        if (!isStep1Completed(profile)) {
            throw new BusinessException("Onboarding step 1 must be completed first");
        }

        profile.setActivitySectorCode(onboardingCatalogService.normalizeAndValidateActivitySectorCode(request.activitySectorCode()));
        profile.setBusinessExperience(request.businessExperience());
        profile.setMonthlyIncomeBracketCode(onboardingCatalogService.normalizeAndValidateIncomeBracketCode(request.monthlyIncomeBracketCode()));
        profile.setProjectDescription(request.projectDescription().trim());
        updateOnboardingState(profile);

        UserProfile saved = userProfileRepository.save(profile);
        return buildOnboardingStatus(user, saved);
    }

    @Transactional
    public OnboardingStatusResponse onboardingStep3(UUID userId, OnboardingStep3Request request) {
        User user = findUser(userId);
        ensureAccountActive(user);

        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("Onboarding step 1 must be completed first"));

        if (!isStep1Completed(profile)) {
            throw new BusinessException("Onboarding step 1 must be completed first");
        }
        if (!isStep2Completed(profile)) {
            throw new BusinessException("Onboarding step 2 must be completed first");
        }
        if (!Boolean.TRUE.equals(request.solidarityGuaranteeAccepted())) {
            throw new BusinessException("Solidarity guarantee acceptance is required");
        }
        if (!Boolean.TRUE.equals(request.termsAccepted())) {
            throw new BusinessException("Terms acceptance is required");
        }

        profile.setAvailableCapital(request.availableCapital());
        profile.setSolidarityGuaranteeAccepted(true);
        profile.setTermsAccepted(true);
        if (profile.getTermsAcceptedAt() == null) {
            profile.setTermsAcceptedAt(OffsetDateTime.now());
        }
        updateOnboardingState(profile);

        UserProfile saved = userProfileRepository.save(profile);
        return buildOnboardingStatus(user, saved);
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

    private UserProfile getOrCreateProfile(User user) {
        return userProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> UserProfile.builder()
                        .user(user)
                        .onboardingStep(OnboardingStep.STEP_1)
                        .onboardingCompleted(false)
                        .country(DEFAULT_COUNTRY_CODE)
                        .phone("")
                        .build());
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

    private void ensureDelegatedIdentityModel(User user) {
        UserProfile profile = userProfileRepository.findByUserId(user.getId()).orElse(null);
        accountHolderService.ensureFromLegacyUser(user, profile);

        appUserRepository.findById(user.getId())
                .or(() -> appUserRepository.findByFirebaseUid(user.getFirebaseUid()))
                .orElseGet(() -> appUserRepository.save(AppUser.builder()
                        .id(user.getId())
                        .firebaseUid(user.getFirebaseUid())
                        .accountHolderId(user.getId())
                        .role(user.getRole())
                        .createdAt(user.getCreatedAt())
                        .build()));
    }

    private void updateOnboardingState(UserProfile profile) {
        boolean step1Completed = isStep1Completed(profile);
        boolean step2Completed = step1Completed && isStep2Completed(profile);
        boolean step3Completed = step2Completed && isStep3Completed(profile);

        OnboardingStep onboardingStep = resolveOnboardingStep(step1Completed, step2Completed, step3Completed);
        profile.setOnboardingStep(onboardingStep);
        profile.setOnboardingCompleted(step3Completed);
        if (step3Completed) {
            if (profile.getOnboardingCompletedAt() == null) {
                profile.setOnboardingCompletedAt(OffsetDateTime.now());
            }
        } else {
            profile.setOnboardingCompletedAt(null);
        }
        if (!profile.isTermsAccepted()) {
            profile.setTermsAcceptedAt(null);
        }
    }

    private boolean isStep1Completed(UserProfile profile) {
        return hasText(profile.getFirstName())
                && hasText(profile.getLastName())
                && hasText(profile.getPhone());
    }

    private boolean isStep2Completed(UserProfile profile) {
        return hasText(profile.getActivitySectorCode())
                && profile.getBusinessExperience() != null
                && hasText(profile.getMonthlyIncomeBracketCode())
                && hasText(profile.getProjectDescription())
                && profile.getProjectDescription().trim().length() >= 20;
    }

    private boolean isStep3Completed(UserProfile profile) {
        return profile.getAvailableCapital() != null
                && profile.getAvailableCapital().compareTo(BigDecimal.ZERO) >= 0
                && profile.isSolidarityGuaranteeAccepted()
                && profile.isTermsAccepted();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private OnboardingStep resolveOnboardingStep(boolean step1Completed, boolean step2Completed, boolean step3Completed) {
        if (step3Completed) {
            return OnboardingStep.COMPLETED;
        }
        if (step2Completed) {
            return OnboardingStep.STEP_3;
        }
        if (step1Completed) {
            return OnboardingStep.STEP_2;
        }
        return OnboardingStep.STEP_1;
    }

    private OnboardingStatusResponse buildOnboardingStatus(User user, UserProfile profile) {
        boolean step1Completed = profile != null && isStep1Completed(profile);
        boolean step2Completed = profile != null && step1Completed && isStep2Completed(profile);
        boolean step3Completed = profile != null && step2Completed && isStep3Completed(profile);
        OnboardingStep onboardingStep = resolveOnboardingStep(step1Completed, step2Completed, step3Completed);
        BigDecimal progressPercentage = BigDecimal.valueOf(((step1Completed ? 1 : 0) + (step2Completed ? 1 : 0) + (step3Completed ? 1 : 0)) * 100L)
                .divide(BigDecimal.valueOf(ONBOARDING_TOTAL_STEPS), 2, RoundingMode.HALF_UP);
        int currentStep = step3Completed ? 3 : (step2Completed ? 3 : (step1Completed ? 2 : 1));

        return new OnboardingStatusResponse(
                user.getId(),
                onboardingStep,
                currentStep,
                ONBOARDING_TOTAL_STEPS,
                progressPercentage,
                step1Completed,
                step2Completed,
                step3Completed,
                step3Completed,
                profile != null ? profile.getOnboardingCompletedAt() : null,
                profile != null ? identityMapper.toDto(profile) : null
        );
    }

    private record SessionCreation(UserSession session, String rawRefreshToken) {
    }
}
