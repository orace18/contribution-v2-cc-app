package com.ccapp.config.security;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.domain.enums.UserRole;
import com.ccapp.identity.service.FirebaseTokenVerifier;
import com.ccapp.identity.service.FirebaseUserClaims;
import com.ccapp.identity.service.UserProvisioningService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FirebaseAuthenticationFilterTest {

    @Mock
    private FirebaseTokenVerifier tokenVerifier;
    @Mock
    private UserProvisioningService userProvisioningService;
    @Mock
    private FilterChain filterChain;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_shouldUseCanonicalAppUserResolvedFromFirebaseClaims() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountHolderId = UUID.randomUUID();
        FirebaseUserClaims claims = new FirebaseUserClaims("firebase-uid", "member@ccapp.test", "+22961000000");
        AppUser appUser = AppUser.builder()
                .id(userId)
                .firebaseUid("firebase-uid")
                .accountHolderId(accountHolderId)
                .role(UserRole.USER)
                .build();
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/identity/country");
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FirebaseAuthenticationFilter filter = new FirebaseAuthenticationFilter(tokenVerifier, userProvisioningService);

        when(tokenVerifier.verify("token")).thenReturn(claims);
        when(userProvisioningService.resolveForClaims(claims)).thenReturn(appUser);

        filter.doFilter(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AuthenticatedPrincipal principal = assertInstanceOf(
                AuthenticatedPrincipal.class,
                authentication.getPrincipal()
        );
        assertEquals(userId, principal.userId());
        assertEquals(accountHolderId, principal.accountHolderId());
        assertEquals("firebase-uid", principal.firebaseUid());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_shouldReturnConflictWhenIdentityGraphIsAmbiguous() throws Exception {
        FirebaseUserClaims claims = new FirebaseUserClaims("firebase-uid", "member@ccapp.test", null);
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/identity/country");
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FirebaseAuthenticationFilter filter = new FirebaseAuthenticationFilter(tokenVerifier, userProvisioningService);

        when(tokenVerifier.verify("token")).thenReturn(claims);
        when(userProvisioningService.resolveForClaims(claims))
                .thenThrow(new BusinessException("IDENTITY_GRAPH_AMBIGUOUS"));

        filter.doFilter(request, response, filterChain);

        assertEquals(HttpStatus.CONFLICT.value(), response.getStatus());
        assertTrue(response.getContentAsString().contains("IDENTITY_GRAPH_AMBIGUOUS"));
        verify(filterChain, never()).doFilter(request, response);
    }
}
