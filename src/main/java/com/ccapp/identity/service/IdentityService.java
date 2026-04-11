package com.ccapp.identity.service;

import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.dto.LinkAssistedAccountRequest;
import com.ccapp.identity.dto.LinkAssistedAccountResponse;
import com.ccapp.identity.dto.UpdateNameRequest;
import com.ccapp.identity.dto.MeResponse;
import com.ccapp.identity.dto.OnboardingRequest;
import com.ccapp.identity.dto.OnboardingStatusResponse;
import com.ccapp.identity.dto.OnboardingStep1Request;
import com.ccapp.identity.dto.OnboardingStep2Request;
import com.ccapp.identity.dto.OnboardingStep3Request;
import com.ccapp.identity.dto.RefreshTokenRequest;
import com.ccapp.identity.dto.RefreshTokenResponse;
import com.ccapp.identity.dto.RegisterDeviceRequest;
import com.ccapp.identity.dto.SyncRequest;
import com.ccapp.identity.dto.SyncResponse;
import com.ccapp.identity.dto.UserDeviceDto;
import com.ccapp.identity.domain.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdentityService {

    private final IdentityAuthService identityAuthService;
    private final IdentityOnboardingService identityOnboardingService;
    private final IdentityProfileService identityProfileService;
    private final IdentityDelegationService identityDelegationService;
    private final IdentityDeviceSessionService identityDeviceSessionService;
    private final AccountHolderService accountHolderService;

    @Transactional
    public SyncResponse sync(FirebaseUserClaims claims, SyncRequest request) {
        return identityDeviceSessionService.sync(claims, request);
    }

    @Transactional
    public User ensureUser(String firebaseUid) {
        return identityDeviceSessionService.ensureUser(firebaseUid, null);
    }

    @Transactional
    public User ensureUser(String firebaseUid, String email) {
        return identityDeviceSessionService.ensureUser(firebaseUid, email);
    }

    @Transactional
    public void selectCountry(UUID userId, String countryCode) {
        identityOnboardingService.selectCountry(userId, countryCode);
    }

    @Transactional
    public MeResponse onboarding(UUID userId, OnboardingRequest request) {
        identityOnboardingService.onboardingStep1(userId, new OnboardingStep1Request(
                request.firstName(),
                request.lastName(),
                request.country(),
                request.phone()
        ));
        return identityProfileService.me(userId);
    }

    @Transactional
    public OnboardingStatusResponse getOnboardingStatus(UUID userId) {
        return identityOnboardingService.getOnboardingStatus(userId);
    }

    @Transactional
    public OnboardingStatusResponse onboardingStep1(UUID userId, OnboardingStep1Request request) {
        return identityOnboardingService.onboardingStep1(userId, request);
    }

    @Transactional
    public OnboardingStatusResponse onboardingStep2(UUID userId, OnboardingStep2Request request) {
        return identityOnboardingService.onboardingStep2(userId, request);
    }

    @Transactional
    public OnboardingStatusResponse onboardingStep3(UUID userId, OnboardingStep3Request request) {
        return identityOnboardingService.onboardingStep3(userId, request);
    }

    @Transactional(readOnly = true)
    public MeResponse me(UUID userId) {
        return identityProfileService.me(userId);
    }

    @Transactional
    public UserDeviceDto registerDevice(UUID userId, RegisterDeviceRequest request) {
        return identityDeviceSessionService.registerDevice(userId, request);
    }

    @Transactional
    public void blockDevice(UUID userId, UUID deviceId) {
        identityDeviceSessionService.blockDevice(userId, deviceId);
    }

    @Transactional
    public RefreshTokenResponse rotateRefreshToken(RefreshTokenRequest request) {
        return identityDeviceSessionService.rotateRefreshToken(request);
    }

    @Transactional
    public void revokeSession(UUID userId, UUID sessionId) {
        identityDeviceSessionService.revokeSession(userId, sessionId);
    }

    @Transactional(readOnly = true)
    public User findUser(UUID userId) {
        return identityDeviceSessionService.findUser(userId);
    }

    @Transactional
    public LinkAssistedAccountResponse linkAssistedAccount(String firebaseUid,
                                                           String email,
                                                           LinkAssistedAccountRequest request) {
        return identityDelegationService.linkAssistedAccount(firebaseUid, email, request);
    }

    @Transactional
    public void updateName(UUID userId, UpdateNameRequest request) {
        accountHolderService.updateName(userId, request.firstName(), request.lastName());
    }

    @Transactional
    public void blockAccount(UUID userId, String reason) {
        identityAuthService.blockAccount(userId, reason);
    }
}
