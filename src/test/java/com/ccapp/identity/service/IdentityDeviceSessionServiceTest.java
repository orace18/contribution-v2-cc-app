package com.ccapp.identity.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.security.ClientRequestMetadata;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountCreatedVia;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    private AccountHolderRepository accountHolderRepository;
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
                accountHolderRepository,
                appUserRepository,
                refreshTokenService,
                identitySecurityService,
                kycStatusConsistencyService
        );
        ReflectionTestUtils.setField(identityDeviceSessionService, "refreshTtlHours", 720L);
        ReflectionTestUtils.setField(identityDeviceSessionService, "maxDevicesPerUser", 5);
    }

    // ── Cas A : firebaseUid connu → réutiliser ──────────────────────────────

    @Test
    void ensureUser_shouldReturnExistingUserWhenFirebaseUidFound() {
        UUID userId = UUID.randomUUID();
        User existingUser = buildActiveUser(userId, "firebase-existing", "member@ccapp.test");
        when(userRepository.findByFirebaseUid("firebase-existing")).thenReturn(Optional.of(existingUser));

        User result = identityDeviceSessionService.ensureUser("firebase-existing", "member@ccapp.test");

        assertSame(existingUser, result);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void ensureUser_shouldRefreshEmailWhenFirebaseUidFoundAndEmailChanged() {
        UUID userId = UUID.randomUUID();
        User existingUser = buildActiveUser(userId, "firebase-existing", "old@ccapp.test");
        when(userRepository.findByFirebaseUid("firebase-existing")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        User result = identityDeviceSessionService.ensureUser("firebase-existing", "new@ccapp.test");

        assertSame(existingUser, result);
        assertEquals("new@ccapp.test", result.getEmail());
        verify(userRepository).save(existingUser);
    }

    // ── Cas B : email existant, firebaseUid différent → rattacher ──────────

    @Test
    void ensureUser_shouldRelinkWhenEmailExistsWithDifferentFirebaseUid() {
        UUID userId = UUID.randomUUID();
        User existingUser = buildActiveUser(userId, "old-firebase-uid", "member@ccapp.test");
        when(userRepository.findByFirebaseUid("new-firebase-uid")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("member@ccapp.test")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        User result = identityDeviceSessionService.ensureUser("new-firebase-uid", "member@ccapp.test");

        assertSame(existingUser, result);
        assertEquals("new-firebase-uid", result.getFirebaseUid());
        verify(userRepository).save(existingUser);
        // Ne crée pas de doublon
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("new-firebase-uid", captor.getValue().getFirebaseUid());
    }

    // ── Cas C : migration V1 → email existant sans firebaseUid cohérent ────

    @Test
    void ensureUser_shouldRelinkForV1MigrationByEmail() {
        UUID legacyUserId = UUID.randomUUID();
        // Simuler un utilisateur V1 qui a un firebase_uid placeholder différent
        User legacyUser = buildActiveUser(legacyUserId, "v1-placeholder-uid", "legacy@ccapp.test");
        when(userRepository.findByFirebaseUid("new-firebase-uid")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("legacy@ccapp.test")).thenReturn(Optional.of(legacyUser));
        when(userRepository.save(legacyUser)).thenReturn(legacyUser);

        User result = identityDeviceSessionService.ensureUser("new-firebase-uid", "legacy@ccapp.test");

        assertEquals(legacyUserId, result.getId());
        assertEquals("new-firebase-uid", result.getFirebaseUid());
        verify(userRepository).save(legacyUser);
    }

    // ── Cas E : managed account détecté par phone ───────────────────────────

    @Test
    void ensureUser_shouldRejectWhenManagedAccountExistsForPhoneWithoutAutonomousIdentity() {
        UUID managedId = UUID.randomUUID();
        AccountHolder managedHolder = AccountHolder.builder()
                .id(managedId)
                .firstName("Jean")
                .lastName("Managed")
                .phoneNumber("+22890123456")
                .createdVia(AccountCreatedVia.MANAGED_ACCOUNT)
                .build();

        when(userRepository.findByFirebaseUid("new-uid")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        when(accountHolderRepository.findByPhoneNumber("+22890123456")).thenReturn(Optional.of(managedHolder));
        when(appUserRepository.findByAccountHolderId(managedId)).thenReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                identityDeviceSessionService.ensureUser("new-uid", "jean@ccapp.test", "+22890123456"));

        assertEquals("MANAGED_ACCOUNT_REQUIRES_RELEASE_CONFIRMATION", ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void ensureUser_shouldAllowSyncWhenManagedAccountPhoneAlreadyHasAutonomousIdentity() {
        UUID managedId = UUID.randomUUID();
        AccountHolder managedHolder = AccountHolder.builder()
                .id(managedId)
                .phoneNumber("+22890123456")
                .createdVia(AccountCreatedVia.MANAGED_ACCOUNT)
                .build();
        AppUser existingAppUser = AppUser.builder().id(managedId).firebaseUid("prev-uid").accountHolderId(managedId).role(UserRole.USER).build();

        when(userRepository.findByFirebaseUid("new-uid")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        when(accountHolderRepository.findByPhoneNumber("+22890123456")).thenReturn(Optional.of(managedHolder));
        when(appUserRepository.findByAccountHolderId(managedId)).thenReturn(List.of(existingAppUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        // Ne doit PAS lever d'exception — le managed account a déjà une identité autonome
        User result = identityDeviceSessionService.ensureUser("new-uid", "jean@ccapp.test", "+22890123456");

        assertNotNull(result);
        verify(userRepository).save(any(User.class));
    }

    // ── Cas G : nouvel utilisateur → créer ─────────────────────────────────

    @Test
    void ensureUser_shouldCreateNewUserWhenNeitherFirebaseUidNorEmailFound() {
        when(userRepository.findByFirebaseUid("firebase-new")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("member@ccapp.test")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        User result = identityDeviceSessionService.ensureUser("firebase-new", "member@ccapp.test", null);

        assertNotNull(result.getId());
        assertEquals("firebase-new", result.getFirebaseUid());
        assertEquals("member@ccapp.test", result.getEmail());
        assertEquals(UserRole.USER, result.getRole());
        assertEquals(AccountStatus.ACTIVE, result.getAccountStatus());
        assertEquals(KycStatus.NOT_STARTED, result.getKycStatus());
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
        User existingUser = buildActiveUser(userId, "firebase-existing", "old@ccapp.test");
        when(userRepository.findByFirebaseUid("firebase-existing")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        User ensured = identityDeviceSessionService.ensureUser("firebase-existing", "new@ccapp.test");

        assertSame(existingUser, ensured);
        assertEquals("new@ccapp.test", ensured.getEmail());
        verify(userRepository).save(existingUser);
    }

    // ── Idempotence : pas de doublon lors de plusieurs syncs ────────────────

    @Test
    void ensureUser_shouldNotCreateDuplicateWhenCalledTwiceForSameFirebaseUid() {
        UUID userId = UUID.randomUUID();
        User existingUser = buildActiveUser(userId, "firebase-uid", "member@ccapp.test");
        when(userRepository.findByFirebaseUid("firebase-uid")).thenReturn(Optional.of(existingUser));

        User first = identityDeviceSessionService.ensureUser("firebase-uid", "member@ccapp.test");
        User second = identityDeviceSessionService.ensureUser("firebase-uid", "member@ccapp.test");

        assertSame(existingUser, first);
        assertSame(existingUser, second);
        // Aucune création : save n'est appelé que si l'email change (syncUserClaims)
        verify(userRepository, never()).findByEmailIgnoreCase(any());
    }

    // ── Device limit ────────────────────────────────────────────────────────

    @Test
    void sync_shouldRejectWhenDeviceLimitExceeded() {
        UUID userId = UUID.randomUUID();
        User existingUser = buildActiveUser(userId, "firebase-uid", "member@ccapp.test");

        when(identitySecurityService.currentRequestMetadata()).thenReturn(new ClientRequestMetadata("203.0.113.20", "BJ", null));
        when(userRepository.findByFirebaseUid("firebase-uid")).thenReturn(Optional.of(existingUser));
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(AppUser.builder()
                .id(userId).firebaseUid("firebase-uid").accountHolderId(userId).role(UserRole.USER).build()));
        when(accountHolderService.exists(userId)).thenReturn(true);
        when(kycStatusConsistencyService.reconcileAccountHolderStatus(userId)).thenReturn(KycStatus.NOT_STARTED);
        when(userDeviceRepository.findByUserIdAndDeviceIdentifierAndDeletedFalse(userId, "device-over-limit")).thenReturn(Optional.empty());
        when(userDeviceRepository.countByUserIdAndDeletedFalse(userId)).thenReturn(5L);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                identityDeviceSessionService.sync(
                        new FirebaseUserClaims("firebase-uid", "member@ccapp.test", null),
                        new SyncRequest("device-over-limit", Platform.ANDROID, "push-token")));

        assertEquals("DEVICE_LIMIT_EXCEEDED", exception.getMessage());
    }

    // ── Sync complet avec utilisateur existant ──────────────────────────────

    @Test
    void sync_shouldReturnSessionForExistingBackendUser() {
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        User existingUser = buildActiveUser(userId, "firebase-uid", "member@ccapp.test");
        AppUser existingAppUser = AppUser.builder().id(userId).firebaseUid("firebase-uid").accountHolderId(userId).role(UserRole.USER).build();

        when(identitySecurityService.currentRequestMetadata()).thenReturn(new ClientRequestMetadata("203.0.113.20", "BJ", null));
        when(userRepository.findByFirebaseUid("firebase-uid")).thenReturn(Optional.of(existingUser));
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(existingAppUser));
        when(accountHolderService.exists(userId)).thenReturn(true);
        when(kycStatusConsistencyService.reconcileAccountHolderStatus(userId)).thenReturn(KycStatus.NOT_STARTED);
        when(userDeviceRepository.findByUserIdAndDeviceIdentifierAndDeletedFalse(userId, "device-1")).thenReturn(Optional.empty());
        when(userDeviceRepository.countByUserIdAndDeletedFalse(userId)).thenReturn(0L);
        when(userDeviceRepository.save(any(UserDevice.class))).thenAnswer(inv -> {
            UserDevice d = inv.getArgument(0);
            d.setId(deviceId);
            return d;
        });
        when(identitySecurityService.assessLoginRisk(eq(userId), any(UserDevice.class), eq(true), eq(false), eq("203.0.113.20"), eq("BJ")))
                .thenReturn(SecurityRiskLevel.LOW);
        when(refreshTokenService.generateToken()).thenReturn("raw-refresh-token");
        when(refreshTokenService.hash("raw-refresh-token")).thenReturn("hashed-refresh-token");
        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(inv -> {
            UserSession s = inv.getArgument(0);
            s.setId(sessionId);
            return s;
        });

        SyncResponse response = identityDeviceSessionService.sync(
                new FirebaseUserClaims("firebase-uid", "member@ccapp.test", null),
                new SyncRequest("device-1", Platform.ANDROID, "push-token"));

        assertEquals(userId, response.userId());
        assertEquals("firebase-uid", response.firebaseUid());
        assertEquals("member@ccapp.test", response.email());
        assertEquals(sessionId, response.sessionId());
        assertEquals("raw-refresh-token", response.refreshToken());
    }

    // ── Sync ne génère pas de 500 quand l'email est déjà connu ─────────────

    @Test
    void sync_shouldNotThrow500WhenEmailAlreadyBoundToDifferentFirebaseUid() {
        UUID existingUserId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        User existingUser = buildActiveUser(existingUserId, "old-uid", "shared@ccapp.test");

        when(identitySecurityService.currentRequestMetadata()).thenReturn(new ClientRequestMetadata("1.2.3.4", "FR", null));
        when(userRepository.findByFirebaseUid("new-uid")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("shared@ccapp.test")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(existingUser)).thenReturn(existingUser);
        when(userProfileRepository.findByUserId(existingUserId)).thenReturn(Optional.empty());
        when(appUserRepository.findById(existingUserId)).thenReturn(Optional.of(
                AppUser.builder().id(existingUserId).firebaseUid("new-uid").accountHolderId(existingUserId).role(UserRole.USER).build()));
        when(accountHolderService.exists(existingUserId)).thenReturn(true);
        when(kycStatusConsistencyService.reconcileAccountHolderStatus(existingUserId)).thenReturn(KycStatus.NOT_STARTED);
        when(userDeviceRepository.findByUserIdAndDeviceIdentifierAndDeletedFalse(existingUserId, "dev-1")).thenReturn(Optional.empty());
        when(userDeviceRepository.countByUserIdAndDeletedFalse(existingUserId)).thenReturn(0L);
        when(userDeviceRepository.save(any(UserDevice.class))).thenAnswer(inv -> {
            UserDevice d = inv.getArgument(0);
            d.setId(deviceId);
            return d;
        });
        when(identitySecurityService.assessLoginRisk(any(UUID.class), any(UserDevice.class), anyBoolean(), anyBoolean(), any(), any())).thenReturn(SecurityRiskLevel.LOW);
        when(refreshTokenService.generateToken()).thenReturn("token");
        when(refreshTokenService.hash("token")).thenReturn("hash");
        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(inv -> {
            UserSession s = inv.getArgument(0);
            s.setId(sessionId);
            return s;
        });

        // Ne doit PAS lever DataIntegrityViolationException → pas de 500
        SyncResponse response = identityDeviceSessionService.sync(
                new FirebaseUserClaims("new-uid", "shared@ccapp.test", null),
                new SyncRequest("dev-1", Platform.ANDROID, null));

        assertEquals(existingUserId, response.userId());
        assertEquals("new-uid", response.firebaseUid());
    }

    // ── Cas A + syncUserClaims : email Firebase déjà lié à un autre utilisateur ──

    @Test
    void ensureUser_shouldNotUpdateEmailWhenClaimedEmailAlreadyBoundToAnotherUser() {
        UUID userAId = UUID.randomUUID();
        UUID userBId = UUID.randomUUID();
        User userA = buildActiveUser(userAId, "firebase-uid-A", null);
        User userB = buildActiveUser(userBId, "firebase-uid-B", "shared@ccapp.test");

        when(userRepository.findByFirebaseUid("firebase-uid-A")).thenReturn(Optional.of(userA));
        when(userRepository.findByEmailIgnoreCase("shared@ccapp.test")).thenReturn(Optional.of(userB));

        User result = identityDeviceSessionService.ensureUser("firebase-uid-A", "shared@ccapp.test");

        assertSame(userA, result);
        assertNull(result.getEmail()); // email NON mis à jour — conflit avec userB
        verify(userRepository, never()).save(any(User.class));
    }

    // ── Cas D : AccountHolder V1 STANDARD par téléphone → rattacher ─────────

    @Test
    void ensureUser_shouldRelinkV1StandardAccountHolderByPhoneWhenNoAutonomousIdentity() {
        UUID v1HolderId = UUID.randomUUID();
        AccountHolder v1Holder = AccountHolder.builder()
                .id(v1HolderId)
                .firstName("Paul")
                .lastName("V1")
                .phoneNumber("+22891234567")
                .createdVia(AccountCreatedVia.SELF)
                .build();

        when(userRepository.findByFirebaseUid("new-uid")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("paul@ccapp.test")).thenReturn(Optional.empty());
        when(accountHolderRepository.findByPhoneNumber("+22891234567")).thenReturn(Optional.of(v1Holder));
        when(appUserRepository.findByAccountHolderId(v1HolderId)).thenReturn(List.of());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = identityDeviceSessionService.ensureUser("new-uid", "paul@ccapp.test", "+22891234567");

        assertNotNull(result);
        assertEquals("new-uid", result.getFirebaseUid());
        assertEquals("paul@ccapp.test", result.getEmail());

        // Vérifie que l'AppUser pointe vers le V1 AccountHolder, pas un nouveau
        ArgumentCaptor<AppUser> appUserCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(appUserCaptor.capture());
        assertEquals(v1HolderId, appUserCaptor.getValue().getAccountHolderId());
    }

    @Test
    void ensureUser_shouldPassThroughWhenV1AccountHolderByPhoneAlreadyHasAutonomousIdentity() {
        UUID v1HolderId = UUID.randomUUID();
        AccountHolder v1Holder = AccountHolder.builder()
                .id(v1HolderId)
                .phoneNumber("+22891234567")
                .createdVia(AccountCreatedVia.SELF)
                .build();
        AppUser existingAppUser = AppUser.builder().id(v1HolderId).firebaseUid("prev-uid").accountHolderId(v1HolderId).role(UserRole.USER).build();

        when(userRepository.findByFirebaseUid("new-uid")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        when(accountHolderRepository.findByPhoneNumber("+22891234567")).thenReturn(Optional.of(v1Holder));
        when(appUserRepository.findByAccountHolderId(v1HolderId)).thenReturn(List.of(existingAppUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        // L'AccountHolder V1 a déjà une identité autonome → créer un nouvel utilisateur normalement
        User result = identityDeviceSessionService.ensureUser("new-uid", "paul@ccapp.test", "+22891234567");

        assertNotNull(result);
        assertEquals("new-uid", result.getFirebaseUid());
        // Pas d'AppUser créé dans ensureUser (le create normal se fait dans ensureDelegatedIdentityModel)
        verify(appUserRepository, never()).save(any(AppUser.class));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private User buildActiveUser(UUID id, String firebaseUid, String email) {
        return User.builder()
                .id(id)
                .firebaseUid(firebaseUid)
                .email(email)
                .role(UserRole.USER)
                .accountStatus(AccountStatus.ACTIVE)
                .kycStatus(KycStatus.NOT_STARTED)
                .build();
    }
}
