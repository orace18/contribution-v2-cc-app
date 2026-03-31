package com.ccapp.mobile.auth.service;

import com.ccapp.common.exception.UnauthorizedException;
import com.ccapp.common.security.ClientRequestMetadata;
import com.ccapp.identity.dto.SyncRequest;
import com.ccapp.identity.dto.SyncResponse;
import com.ccapp.identity.service.FirebaseTokenVerifier;
import com.ccapp.identity.service.FirebaseUserClaims;
import com.ccapp.identity.service.IdentitySecurityService;
import com.ccapp.identity.service.IdentityService;
import com.ccapp.mobile.auth.dto.MobileSocialLoginRequest;
import com.ccapp.mobile.auth.dto.MobileSocialLoginResponse;
import com.ccapp.mobile.onboarding.service.MobileOnboardingJourneyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MobileAuthService {

    private final IdentityService identityService;
    private final FirebaseTokenVerifier firebaseTokenVerifier;
    private final IdentitySecurityService identitySecurityService;
    private final MobileOnboardingJourneyService mobileOnboardingJourneyService;

    @Transactional
    public MobileSocialLoginResponse socialLogin(MobileSocialLoginRequest request) {
        ClientRequestMetadata metadata = identitySecurityService.currentRequestMetadata();
        identitySecurityService.assertLoginAllowed(metadata.ipAddress(), request.deviceIdentifier());

        SyncResponse sync = null;
        boolean succeeded = false;
        try {
            FirebaseUserClaims claims = resolveClaims(request.idToken());
            identitySecurityService.assertFreshFirebaseToken(
                    request.idToken().trim(),
                    request.deviceIdentifier(),
                    metadata.ipAddress(),
                    metadata.countryCode()
            );
            sync = identityService.sync(claims, new SyncRequest(
                    request.deviceIdentifier(),
                    request.platform(),
                    request.pushToken()
            ));

            identitySecurityService.recordFirebaseTokenUsage(
                    request.idToken().trim(),
                    sync.userId(),
                    request.deviceIdentifier()
            );

            UUID accountHolderId = sync.userId();
            mobileOnboardingJourneyService.markAuthenticated(accountHolderId);
            succeeded = true;

            return new MobileSocialLoginResponse(
                    sync.userId(),
                    accountHolderId,
                    sync.firebaseUid(),
                    sync.email(),
                    sync.role(),
                    sync.accountStatus(),
                    sync.kycStatus(),
                    sync.sessionId(),
                    sync.refreshToken()
            );
        } finally {
            identitySecurityService.recordLoginAttempt(
                    metadata.ipAddress(),
                    request.deviceIdentifier(),
                    sync == null ? null : sync.userId(),
                    succeeded
            );
        }
    }

    private FirebaseUserClaims resolveClaims(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new UnauthorizedException("Firebase token is required");
        }
        return firebaseTokenVerifier.verify(idToken.trim());
    }
}
