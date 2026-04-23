package com.ccapp.identity.service;

import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.domain.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentIdentityServiceTest {

    @Mock
    private UserProvisioningService userProvisioningService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolveCurrentAppUser_shouldIgnoreStalePrincipalIdsAndUseFirebaseClaims() {
        UUID staleUserId = UUID.randomUUID();
        UUID staleAccountHolderId = UUID.randomUUID();
        UUID canonicalUserId = UUID.randomUUID();
        FirebaseUserClaims claims = new FirebaseUserClaims(
                "firebase-uid",
                "member@ccapp.test",
                "+22961000000"
        );
        AppUser canonical = AppUser.builder()
                .id(canonicalUserId)
                .firebaseUid(claims.uid())
                .accountHolderId(canonicalUserId)
                .role(UserRole.USER)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                AuthenticatedPrincipal.builder()
                        .firebaseUid(claims.uid())
                        .email(claims.email())
                        .phoneNumber(claims.phoneNumber())
                        .userId(staleUserId)
                        .accountHolderId(staleAccountHolderId)
                        .build(),
                null
        ));
        when(userProvisioningService.resolveForClaims(claims)).thenReturn(canonical);

        AppUser resolved = new CurrentIdentityService(userProvisioningService).resolveCurrentAppUser();

        assertThat(resolved.getId()).isEqualTo(canonicalUserId);
        assertThat(resolved.getAccountHolderId()).isEqualTo(canonicalUserId);
    }
}
