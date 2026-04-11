package com.ccapp.identity.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.security.ClientRequestMetadata;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.domain.entity.User;
import com.ccapp.identity.domain.entity.UserDevice;
import com.ccapp.identity.domain.entity.UserSession;
import com.ccapp.identity.domain.enums.AccountStatus;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.domain.enums.Platform;
import com.ccapp.identity.domain.enums.SecurityRiskLevel;
import com.ccapp.identity.domain.enums.UserRole;
import com.ccapp.identity.dto.SyncRequest;
import com.ccapp.identity.dto.SyncResponse;
import com.ccapp.identity.repository.UserDeviceRepository;
import com.ccapp.identity.repository.UserProfileRepository;
import com.ccapp.identity.repository.UserRepository;
import com.ccapp.identity.repository.UserSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityDeviceSessionServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private UserDeviceRepository userDeviceRepository;
    @Mock
    private UserSessionRepository userSessionRepository;
    @Mock
    private AccountHolderService accountHolderService;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private IdentitySecurityService identitySecurityService;
    @Mock
    private KycStatusConsistencyService kycStatusConsistencyService;

    private IdentityDeviceSessionService identityDeviceSessionService;

    @BeforeEach
    void setUp() {
        identityDeviceSessionService = new IdentityDeviceSessionService(
                userRepository,
                userProfileRepository,
                userDeviceRepository,
                userSessionRepository,
                accountHolderService,
                appUserRepository,
                refreshTokenService,
                identitySecurityService,
                kycStatusConsistencyService
        );
        ReflectionTestUtils.setField(identityDeviceSessionService, "refreshTtlHours", 720L);
        ReflectionTestUtils.setField(identityDeviceSessionService, "maxDevicesPerUser", 5);
    }

    @Test
    void sync_shouldRejectWhenDeviceLimitExceeded() {
        UUID userId = UUID.randomUUID();
        User existingUser = User.builder()
                .id(userId)
                .firebaseUid("firebase-uid")
                .email("member@ccapp.test")
                .role(UserRole.USER)
                .accountStatus(AccountStatus.ACTIVE)
                .kycStatus(KycStatus.NOT_STARTED)
                .build();

        when(identitySecurityService.currentRequestMetadata()).thenReturn(new ClientRequestMetadata("203.0.113.20", "BJ", null));
        when(userRepository.findByFirebaseUid("firebase-uid")).thenReturn(Optional.of(existingUser));
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(AppUser.builder()
                .id(userId)
                .firebaseUid("firebase-uid")
                .accountHolderId(userId)
                .role(UserRole.USER)
                .build()));
        when(accountHolderService.exists(userId)).thenReturn(true);
        when(kycStatusConsistencyService.reconcileAccountHolderStatus(userId)).thenReturn(KycStatus.NOT_STARTED);
        when(userDeviceRepository.findByUserIdAndDeviceIdentifierAndDeletedFalse(userId, "device-over-limit")).thenReturn(Optional.empty());
        when(userDeviceRepository.countByUserIdAndDeletedFalse(userId)).thenReturn(5L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> identityDeviceSessionService.sync(
                        new FirebaseUserClaims("firebase-uid", "member@ccapp.test", null),
                        new SyncRequest("device-over-limit", Platform.ANDROID, "push-token")
                )
        );

        assertEquals("DEVICE_LIMIT_EXCEEDED", exception.getMessage());
    }

    @Test
    void sync_shouldReturnSessionForExistingBackendUser() {
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        User existingUser = User.builder()
                .id(userId)
                .firebaseUid("firebase-uid")
                .email("member@ccapp.test")
                .role(UserRole.USER)
                .accountStatus(AccountStatus.ACTIVE)
                .kycStatus(KycStatus.NOT_STARTED)
                .build();
        AppUser existingAppUser = AppUser.builder()
                .id(userId)
                .firebaseUid("firebase-uid")
                .accountHolderId(userId)
                .role(UserRole.USER)
                .build();

        when(identitySecurityService.currentRequestMetadata()).thenReturn(new ClientRequestMetadata("203.0.113.20", "BJ", null));
        when(userRepository.findByFirebaseUid("firebase-uid")).thenReturn(Optional.of(existingUser));
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(existingAppUser));
        when(accountHolderService.exists(userId)).thenReturn(true);
        when(kycStatusConsistencyService.reconcileAccountHolderStatus(userId)).thenReturn(KycStatus.NOT_STARTED);
        when(userDeviceRepository.findByUserIdAndDeviceIdentifierAndDeletedFalse(userId, "device-1")).thenReturn(Optional.empty());
        when(userDeviceRepository.countByUserIdAndDeletedFalse(userId)).thenReturn(0L);
        when(userDeviceRepository.save(any(UserDevice.class))).thenAnswer(invocation -> {
            UserDevice device = invocation.getArgument(0);
            device.setId(deviceId);
            return device;
        });
        when(identitySecurityService.assessLoginRisk(eq(userId), any(UserDevice.class), eq(true), eq(false), eq("203.0.113.20"), eq("BJ")))
                .thenReturn(SecurityRiskLevel.LOW);
        when(refreshTokenService.generateToken()).thenReturn("raw-refresh-token");
        when(refreshTokenService.hash("raw-refresh-token")).thenReturn("hashed-refresh-token");
        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(invocation -> {
            UserSession session = invocation.getArgument(0);
            session.setId(sessionId);
            return session;
        });

        SyncResponse response = identityDeviceSessionService.sync(
                new FirebaseUserClaims("firebase-uid", "member@ccapp.test", null),
                new SyncRequest("device-1", Platform.ANDROID, "push-token")
        );

        assertEquals(userId, response.userId());
        assertEquals("firebase-uid", response.firebaseUid());
        assertEquals("member@ccapp.test", response.email());
        assertEquals(sessionId, response.sessionId());
        assertEquals("raw-refresh-token", response.refreshToken());
    }

    @Test
    void ensureUser_shouldCreateBackendUserForNewFirebaseIdentity() {
        when(userRepository.findByFirebaseUid("firebase-new")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        User ensured = identityDeviceSessionService.ensureUser("firebase-new", "member@ccapp.test");

        assertNotNull(ensured.getId());
        assertEquals("firebase-new", ensured.getFirebaseUid());
        assertEquals("member@ccapp.test", ensured.getEmail());
        assertEquals(UserRole.USER, ensured.getRole());
        assertEquals(AccountStatus.ACTIVE, ensured.getAccountStatus());
        assertEquals(KycStatus.NOT_STARTED, ensured.getKycStatus());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("firebase-new", userCaptor.getValue().getFirebaseUid());
    }

    @Test
    void ensureUser_shouldReturnExistingBackendUserAndRefreshEmail() {
        UUID userId = UUID.randomUUID();
        User existingUser = User.builder()
                .id(userId)
                .firebaseUid("firebase-existing")
                .email("old@ccapp.test")
                .role(UserRole.USER)
                .accountStatus(AccountStatus.ACTIVE)
                .kycStatus(KycStatus.NOT_STARTED)
                .build();

        when(userRepository.findByFirebaseUid("firebase-existing")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        User ensured = identityDeviceSessionService.ensureUser("firebase-existing", "new@ccapp.test");

        assertSame(existingUser, ensured);
        assertEquals("new@ccapp.test", ensured.getEmail());
        verify(userRepository).save(existingUser);
    }
}
