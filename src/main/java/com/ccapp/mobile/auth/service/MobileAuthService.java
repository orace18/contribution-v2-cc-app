package com.ccapp.mobile.auth.service;

import com.ccapp.common.exception.UnauthorizedException;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.identity.dto.SyncRequest;
import com.ccapp.identity.dto.SyncResponse;
import com.ccapp.identity.service.FirebaseTokenVerifier;
import com.ccapp.identity.service.FirebaseUserClaims;
import com.ccapp.identity.service.IdentityService;
import com.ccapp.mobile.auth.dto.MobileSocialLoginRequest;
import com.ccapp.mobile.auth.dto.MobileSocialLoginResponse;
import com.ccapp.mobile.onboarding.service.MobileOnboardingJourneyService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MobileAuthService {

    private final IdentityService identityService;
    private final FirebaseTokenVerifier firebaseTokenVerifier;
    private final MobileOnboardingJourneyService mobileOnboardingJourneyService;

    @Transactional
    public MobileSocialLoginResponse socialLogin(MobileSocialLoginRequest request) {
        FirebaseUserClaims claims = resolveClaims(request.idToken());
        SyncResponse sync = identityService.sync(claims, new SyncRequest(
                request.deviceIdentifier(),
                request.platform()
        ));

        UUID accountHolderId = sync.userId();
        mobileOnboardingJourneyService.markAuthenticated(accountHolderId);

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
    }

    private FirebaseUserClaims resolveClaims(String idToken) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedPrincipal principal) {
            if (principal.firebaseUid() != null && !principal.firebaseUid().isBlank()) {
                return new FirebaseUserClaims(principal.firebaseUid(), principal.email());
            }
        }

        if (idToken == null || idToken.isBlank()) {
            throw new UnauthorizedException("Firebase token is required");
        }
        return firebaseTokenVerifier.verify(idToken.trim());
    }
}
