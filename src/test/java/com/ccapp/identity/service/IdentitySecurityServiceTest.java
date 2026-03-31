package com.ccapp.identity.service;

import com.ccapp.common.exception.UnauthorizedException;
import com.ccapp.common.security.ClientRequestMetadataResolver;
import com.ccapp.identity.domain.enums.SecurityEventType;
import com.ccapp.identity.domain.enums.SecurityRiskLevel;
import com.ccapp.identity.repository.AuthTokenUsageRepository;
import com.ccapp.identity.repository.LoginAttemptRepository;
import com.ccapp.identity.repository.UserSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentitySecurityServiceTest {

    @Mock
    private AuthTokenUsageRepository authTokenUsageRepository;
    @Mock
    private LoginAttemptRepository loginAttemptRepository;
    @Mock
    private UserSessionRepository userSessionRepository;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private ClientRequestMetadataResolver clientRequestMetadataResolver;
    @Mock
    private IdentitySecurityPersistenceService identitySecurityPersistenceService;

    private IdentitySecurityService identitySecurityService;

    @BeforeEach
    void setUp() {
        identitySecurityService = new IdentitySecurityService(
                authTokenUsageRepository,
                loginAttemptRepository,
                userSessionRepository,
                refreshTokenService,
                clientRequestMetadataResolver,
                identitySecurityPersistenceService
        );
        ReflectionTestUtils.setField(identitySecurityService, "firebaseReplayWindowMinutes", 10L);
    }

    @Test
    void assertFreshFirebaseToken_shouldRejectRecentReplay() {
        when(refreshTokenService.hash("firebase-id-token")).thenReturn("hashed-firebase-token");
        when(authTokenUsageRepository.existsByTokenHashAndUsedAtAfter(eq("hashed-firebase-token"), org.mockito.ArgumentMatchers.any(OffsetDateTime.class)))
                .thenReturn(true);

        UnauthorizedException exception = assertThrows(
                UnauthorizedException.class,
                () -> identitySecurityService.assertFreshFirebaseToken(
                        "firebase-id-token",
                        "device-123456",
                        "203.0.113.10",
                        "BJ"
                )
        );

        assertEquals("FIREBASE_TOKEN_REPLAY_DETECTED", exception.getMessage());
        verify(identitySecurityPersistenceService).saveSecurityEvent(
                null,
                SecurityEventType.TOKEN_REPLAY,
                "device-123456",
                "203.0.113.10",
                SecurityRiskLevel.HIGH,
                "BJ"
        );
    }
}
