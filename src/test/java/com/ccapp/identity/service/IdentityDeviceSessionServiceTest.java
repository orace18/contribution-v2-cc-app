package com.ccapp.identity.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.security.ClientRequestMetadata;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.domain.entity.User;
import com.ccapp.identity.domain.enums.AccountStatus;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.domain.enums.Platform;
import com.ccapp.identity.domain.enums.UserRole;
import com.ccapp.identity.dto.SyncRequest;
import com.ccapp.identity.repository.UserDeviceRepository;
import com.ccapp.identity.repository.UserProfileRepository;
import com.ccapp.identity.repository.UserRepository;
import com.ccapp.identity.repository.UserSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
                identitySecurityService
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
}
