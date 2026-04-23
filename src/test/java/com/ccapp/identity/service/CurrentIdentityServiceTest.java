package com.ccapp.identity.service;

import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.identity.domain.entity.User;
import com.ccapp.identity.domain.enums.AccountStatus;
import com.ccapp.identity.domain.enums.KycStatus;
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
    private IdentityService identityService;
    @Mock
    private UserProvisioningService userProvisioningService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolveCurrentUser_shouldIgnoreStalePrincipalIdsAndUseFirebaseClaims() {
        UUID staleUserId = UUID.randomUUID();
        UUID canonicalUserId = UUID.randomUUID();
        User canonical = User.builder()
                .id(canonicalUserId)
                .firebaseUid("firebase-uid")
                .email("member@ccapp.test")
                .role(UserRole.USER)
                .accountStatus(AccountStatus.ACTIVE)
                .kycStatus(KycStatus.NOT_STARTED)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                AuthenticatedPrincipal.builder()
                        .firebaseUid("firebase-uid")
                        .email("member@ccapp.test")
                        .userId(staleUserId)
                        .build(),
                null
        ));
        when(identityService.ensureUser("firebase-uid", "member@ccapp.test")).thenReturn(canonical);

        User resolved = new CurrentIdentityService(identityService, userProvisioningService).resolveCurrentUser();

        assertThat(resolved.getId()).isEqualTo(canonicalUserId);
        assertThat(resolved.getFirebaseUid()).isEqualTo("firebase-uid");
    }
}
