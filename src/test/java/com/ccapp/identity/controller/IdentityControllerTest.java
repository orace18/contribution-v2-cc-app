package com.ccapp.identity.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.identity.domain.entity.User;
import com.ccapp.identity.domain.enums.AccountStatus;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.domain.enums.OnboardingStep;
import com.ccapp.identity.domain.enums.UserRole;
import com.ccapp.identity.dto.OnboardingStatusResponse;
import com.ccapp.identity.dto.SelectCountryRequest;
import com.ccapp.identity.service.CurrentIdentityService;
import com.ccapp.identity.service.IdentityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdentityControllerTest {

    private final IdentityService identityService = mock(IdentityService.class);
    private final CurrentIdentityService currentIdentityService = mock(CurrentIdentityService.class);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void onboardingStatus_shouldEnsureBackendUserFromFirebaseUidBeforeLoadingStatus() {
        UUID principalUserId = UUID.randomUUID();
        UUID backendUserId = UUID.randomUUID();
        setAuthenticatedPrincipal(principalUserId, "firebase-google-user", "member@ccapp.test");

        IdentityController controller = new IdentityController(identityService, currentIdentityService);
        User backendUser = User.builder()
                .id(backendUserId)
                .firebaseUid("firebase-google-user")
                .email("member@ccapp.test")
                .role(UserRole.USER)
                .accountStatus(AccountStatus.ACTIVE)
                .kycStatus(KycStatus.NOT_STARTED)
                .build();
        OnboardingStatusResponse expected = new OnboardingStatusResponse(
                backendUserId,
                OnboardingStep.STEP_1,
                1,
                3,
                BigDecimal.ZERO,
                false,
                false,
                false,
                false,
                null,
                null
        );

        when(currentIdentityService.resolveCurrentUser()).thenReturn(backendUser);
        when(identityService.getOnboardingStatus(backendUserId)).thenReturn(expected);

        ResponseEntity<CommonResponse<OnboardingStatusResponse>> response = controller.onboardingStatus();

        assertTrue(response.getBody().success());
        assertEquals(expected, response.getBody().data());
        verify(currentIdentityService).resolveCurrentUser();
        verify(identityService).getOnboardingStatus(backendUserId);
        verify(identityService, never()).getOnboardingStatus(principalUserId);
    }

    @Test
    void selectCountry_shouldUseCanonicalUserResolvedFromFirebaseToken() {
        UUID stalePrincipalUserId = UUID.randomUUID();
        UUID canonicalUserId = UUID.randomUUID();
        setAuthenticatedPrincipal(stalePrincipalUserId, "firebase-google-user", "member@ccapp.test");
        IdentityController controller = new IdentityController(identityService, currentIdentityService);

        when(currentIdentityService.resolveCurrentUserId()).thenReturn(canonicalUserId);

        controller.selectCountry(new SelectCountryRequest("BJ"));

        verify(currentIdentityService).resolveCurrentUserId();
        verify(identityService).selectCountry(canonicalUserId, "BJ");
        verify(identityService, never()).selectCountry(stalePrincipalUserId, "BJ");
    }

    private void setAuthenticatedPrincipal(UUID userId, String firebaseUid, String email) {
        AuthenticatedPrincipal principal = AuthenticatedPrincipal.builder()
                .userId(userId)
                .firebaseUid(firebaseUid)
                .email(email)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null)
        );
    }
}
