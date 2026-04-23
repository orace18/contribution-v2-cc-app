package com.ccapp.devtools.service;

import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountCreatedVia;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.domain.entity.User;
import com.ccapp.identity.domain.entity.UserDevice;
import com.ccapp.identity.domain.entity.UserProfile;
import com.ccapp.identity.domain.entity.UserSession;
import com.ccapp.identity.domain.enums.AccountStatus;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.domain.enums.OnboardingStep;
import com.ccapp.identity.domain.enums.Platform;
import com.ccapp.identity.domain.enums.SecurityRiskLevel;
import com.ccapp.identity.domain.enums.UserRole;
import com.ccapp.identity.repository.UserDeviceRepository;
import com.ccapp.identity.repository.UserProfileRepository;
import com.ccapp.identity.repository.UserRepository;
import com.ccapp.identity.repository.UserSessionRepository;
import com.ccapp.identity.verification.domain.entity.PhoneVerification;
import com.ccapp.identity.verification.domain.enums.PhoneVerificationChannel;
import com.ccapp.identity.verification.domain.enums.PhoneVerificationStatus;
import com.ccapp.identity.verification.repository.PhoneVerificationRepository;
import com.ccapp.mobile.onboarding.domain.entity.MobileOnboardingJourney;
import com.ccapp.mobile.onboarding.domain.entity.MobilePlanSelection;
import com.ccapp.mobile.onboarding.domain.enums.MobileOnboardingJourneyStatus;
import com.ccapp.mobile.onboarding.domain.enums.MobilePlanSelectionStatus;
import com.ccapp.mobile.onboarding.repository.MobileOnboardingJourneyRepository;
import com.ccapp.mobile.onboarding.repository.MobilePlanSelectionRepository;
import com.ccapp.notifications.domain.entity.Notification;
import com.ccapp.notifications.domain.enums.NotificationChannel;
import com.ccapp.notifications.domain.enums.NotificationStatus;
import com.ccapp.notifications.domain.enums.NotificationType;
import com.ccapp.notifications.repository.NotificationRepository;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.repository.OperationRepository;
import com.ccapp.payments.subscription.domain.entity.Membership;
import com.ccapp.payments.subscription.domain.enums.MembershipStatus;
import com.ccapp.payments.subscription.repository.MembershipRepository;
import com.ccapp.wallet.domain.entity.WalletLedgerEntry;
import com.ccapp.wallet.domain.entity.WalletOtpChallenge;
import com.ccapp.wallet.domain.entity.WalletReservation;
import com.ccapp.wallet.domain.enums.WalletLedgerCategory;
import com.ccapp.wallet.domain.enums.WalletLedgerEntryType;
import com.ccapp.wallet.domain.enums.WalletLedgerStatus;
import com.ccapp.wallet.domain.enums.WalletOtpChallengeStatus;
import com.ccapp.wallet.domain.enums.WalletReservationStatus;
import com.ccapp.wallet.domain.enums.WalletType;
import com.ccapp.wallet.repository.WalletLedgerEntryRepository;
import com.ccapp.wallet.repository.WalletOtpChallengeRepository;
import com.ccapp.wallet.repository.WalletReservationRepository;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(DevUserResetService.class)
class DevUserResetServiceIntegrationTest {

    @Autowired
    private DevUserResetService devUserResetService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AccountHolderRepository accountHolderRepository;
    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private UserProfileRepository userProfileRepository;
    @Autowired
    private UserDeviceRepository userDeviceRepository;
    @Autowired
    private UserSessionRepository userSessionRepository;
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private OperationRepository operationRepository;
    @Autowired
    private MembershipRepository membershipRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private MobileOnboardingJourneyRepository mobileOnboardingJourneyRepository;
    @Autowired
    private MobilePlanSelectionRepository mobilePlanSelectionRepository;
    @Autowired
    private PhoneVerificationRepository phoneVerificationRepository;
    @Autowired
    private WalletLedgerEntryRepository walletLedgerEntryRepository;
    @Autowired
    private WalletReservationRepository walletReservationRepository;
    @Autowired
    private WalletOtpChallengeRepository walletOtpChallengeRepository;

    @Test
    void deleteUser_shouldDeleteIdentityWalletSubscriptionNotificationAndOnboardingData() {
        UUID userId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        jdbcTemplate.update(
                "INSERT INTO users (id, firebase_uid, email, role, account_status, kyc_status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                userId,
                "firebase-" + userId,
                "reset.qa@clubchretien.dev",
                UserRole.USER.name(),
                AccountStatus.ACTIVE.name(),
                KycStatus.NOT_STARTED.name(),
                now,
                now
        );
        User user = userRepository.findById(userId).orElseThrow();

        AccountHolder accountHolder = accountHolderRepository.save(AccountHolder.builder()
                .id(userId)
                .firstName("Reset")
                .lastName("User")
                .phoneNumber("22991000000")
                .countryCode("BJ")
                .serviceCountryCode("BJ")
                .accountType(AccountType.STANDARD)
                .createdVia(AccountCreatedVia.SELF)
                .kycStatus(KycStatus.NOT_STARTED)
                .build());

        AppUser appUser = appUserRepository.save(AppUser.builder()
                .id(userId)
                .firebaseUid(user.getFirebaseUid())
                .accountHolderId(accountHolder.getId())
                .role(UserRole.USER)
                .build());

        userProfileRepository.save(UserProfile.builder()
                .user(user)
                .firstName("Reset")
                .lastName("User")
                .phone("22991000000")
                .country("BJ")
                .onboardingStep(OnboardingStep.STEP_2)
                .onboardingCompleted(false)
                .build());

        UserDevice device = userDeviceRepository.save(UserDevice.builder()
                .user(user)
                .deviceIdentifier("device-1")
                .platform(Platform.ANDROID)
                .blocked(false)
                .deleted(false)
                .trustedDevice(true)
                .build());

        userSessionRepository.save(UserSession.builder()
                .user(user)
                .device(device)
                .refreshTokenHash("refresh-hash-1")
                .expiresAt(now.plusDays(1))
                .riskLevel(SecurityRiskLevel.LOW)
                .build());

        Wallet wallet = walletRepository.save(Wallet.builder()
                .accountHolderId(accountHolder.getId())
                .provider("MOBILE")
                .mobileMoneyNumber("22991000000")
                .status("ACTIVE")
                .walletType(WalletType.USER)
                .build());

        Operation operation = operationRepository.save(Operation.builder()
                .accountHolderId(accountHolder.getId())
                .actorUserId(appUser.getId())
                .type(OperationType.SUBSCRIPTION)
                .status(OperationStatus.PENDING_PSP)
                .amount(BigDecimal.valueOf(10000))
                .currency("XOF")
                .idempotencyKey("idem-" + userId)
                .planId(UUID.randomUUID())
                .build());

        walletLedgerEntryRepository.save(WalletLedgerEntry.builder()
                .walletId(wallet.getId())
                .operationId(operation.getId())
                .entryType(WalletLedgerEntryType.DEBIT)
                .category(WalletLedgerCategory.PAYMENT)
                .status(WalletLedgerStatus.PENDING)
                .amount(BigDecimal.valueOf(10000))
                .currency("XOF")
                .build());

        walletReservationRepository.save(WalletReservation.builder()
                .walletId(wallet.getId())
                .operationId(operation.getId())
                .amount(BigDecimal.valueOf(10000))
                .reason("QA reservation")
                .status(WalletReservationStatus.ACTIVE)
                .build());

        walletOtpChallengeRepository.save(WalletOtpChallenge.builder()
                .walletId(wallet.getId())
                .accountHolderId(accountHolder.getId())
                .operationId(operation.getId())
                .operationType(OperationType.SUBSCRIPTION)
                .fingerprintHash("fingerprint")
                .otpHash("otp-hash")
                .phoneNumber("22991000000")
                .status(WalletOtpChallengeStatus.PENDING)
                .maxAttempts(3)
                .sentAt(now)
                .expiresAt(now.plusMinutes(5))
                .build());

        membershipRepository.save(Membership.builder()
                .operationId(operation.getId())
                .planId(UUID.randomUUID())
                .accountHolderId(accountHolder.getId())
                .planCode("STARTER")
                .countryCode("BJ")
                .currency("XOF")
                .amount(BigDecimal.valueOf(10000))
                .status(MembershipStatus.PENDING)
                .build());

        notificationRepository.save(Notification.builder()
                .accountHolderId(accountHolder.getId())
                .actorUserId(appUser.getId())
                .type(NotificationType.ACCOUNT_UPDATED)
                .channel(NotificationChannel.IN_APP)
                .message("Profile updated")
                .status(NotificationStatus.PENDING)
                .build());

        mobileOnboardingJourneyRepository.save(MobileOnboardingJourney.builder()
                .accountHolderId(accountHolder.getId())
                .status(MobileOnboardingJourneyStatus.PAYMENT_PENDING)
                .countryCode("BJ")
                .sponsorSet(false)
                .networkLinked(false)
                .onboardingCompleted(false)
                .build());

        mobilePlanSelectionRepository.save(MobilePlanSelection.builder()
                .accountHolderId(accountHolder.getId())
                .planId(UUID.randomUUID())
                .planCode("STARTER")
                .planName("Starter")
                .countryCode("BJ")
                .currency("XOF")
                .status(MobilePlanSelectionStatus.PENDING_PAYMENT)
                .build());

        phoneVerificationRepository.save(PhoneVerification.builder()
                .requesterAccountHolderId(accountHolder.getId())
                .phoneNumber("22991000000")
                .channel(PhoneVerificationChannel.SMS)
                .status(PhoneVerificationStatus.PENDING)
                .codeHash("code-hash")
                .attemptCount(0)
                .maxAttempts(3)
                .sendCount(1)
                .maxSendCount(5)
                .lastSentAt(now)
                .resendAvailableAt(now.plusSeconds(30))
                .expiresAt(now.plusMinutes(5))
                .build());

        UUID kycMediaId = UUID.randomUUID();
        UUID identityVerificationId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO media_files (id, account_holder_id, type, bucket, object_key, content_type, size, status, created_at) " +
                        "VALUES (?, ?, 'KYC_IDENTITY_FRONT', 'dev-kyc', ?, 'image/jpeg', 12, 'READY', ?)",
                kycMediaId,
                accountHolder.getId(),
                "kyc/" + kycMediaId,
                now
        );
        jdbcTemplate.update(
                "INSERT INTO kyc_documents (id, account_holder_id, media_file_id, document_type, status, created_at) " +
                        "VALUES (?, ?, ?, 'IDENTITY_FRONT', 'VERIFIED', ?)",
                UUID.randomUUID(),
                accountHolder.getId(),
                kycMediaId,
                now
        );
        jdbcTemplate.update(
                "INSERT INTO kyc_media_backups (media_file_id, account_holder_id, content_type, payload, created_at) " +
                        "VALUES (?, ?, 'image/jpeg', ?, ?)",
                kycMediaId,
                accountHolder.getId(),
                new byte[]{1, 2, 3},
                now
        );
        jdbcTemplate.update(
                "INSERT INTO identity_verifications (id, account_holder_id, reference_id, status, current_step, " +
                        "document_type, created_at, updated_at) " +
                        "VALUES (?, ?, 'case-full-reset', 'APPROVED', 'COMPLETED', 'NATIONAL_ID', ?, ?)",
                identityVerificationId,
                accountHolder.getId(),
                now,
                now
        );
        jdbcTemplate.update(
                "INSERT INTO verification_documents (id, verification_id, document_front_key, selfie_key, bucket_name, uploaded_at) " +
                        "VALUES (?, ?, 'front-key', 'selfie-key', 'dev-kyc', ?)",
                UUID.randomUUID(),
                identityVerificationId,
                now
        );
        jdbcTemplate.update(
                "INSERT INTO verification_analysis (id, verification_id, document_readable, analyzed_at) " +
                        "VALUES (?, ?, TRUE, ?)",
                UUID.randomUUID(),
                identityVerificationId,
                now
        );

        DevUserResetService.DevUserResetResult result = devUserResetService.deleteUser(
                "reset.qa@clubchretien.dev",
                null,
                UUID.randomUUID(),
                "qa-admin@clubchretien.dev"
        );

        assertTrue(result.deleted());
        assertEquals(userId, result.userId());
        assertEquals(accountHolder.getId(), result.accountHolderId());
        assertEquals("reset.qa@clubchretien.dev", result.email());

        assertFalse(userRepository.findById(userId).isPresent());
        assertFalse(accountHolderRepository.findById(accountHolder.getId()).isPresent());
        assertTrue(appUserRepository.findByAccountHolderId(accountHolder.getId()).isEmpty());
        assertEquals(0L, userProfileRepository.count());
        assertEquals(0L, userDeviceRepository.count());
        assertEquals(0L, userSessionRepository.count());
        assertEquals(0L, walletRepository.count());
        assertEquals(0L, operationRepository.count());
        assertEquals(0L, membershipRepository.count());
        assertEquals(0L, notificationRepository.count());
        assertEquals(0L, mobileOnboardingJourneyRepository.count());
        assertEquals(0L, mobilePlanSelectionRepository.count());
        assertEquals(0L, phoneVerificationRepository.count());
        assertEquals(0L, count("SELECT COUNT(*) FROM kyc_documents WHERE account_holder_id = ?", accountHolder.getId()));
        assertEquals(0L, count("SELECT COUNT(*) FROM kyc_media_backups WHERE account_holder_id = ?", accountHolder.getId()));
        assertEquals(0L, count("SELECT COUNT(*) FROM media_files WHERE id = ?", kycMediaId));
        assertEquals(0L, count("SELECT COUNT(*) FROM identity_verifications WHERE account_holder_id = ?", accountHolder.getId()));
        assertEquals(0L, count("SELECT COUNT(*) FROM verification_documents WHERE verification_id = ?", identityVerificationId));
        assertEquals(0L, count("SELECT COUNT(*) FROM verification_analysis WHERE verification_id = ?", identityVerificationId));
        assertEquals(0L, walletLedgerEntryRepository.count());
        assertEquals(0L, walletReservationRepository.count());
        assertEquals(0L, walletOtpChallengeRepository.count());

        DevUserResetService.DevUserResetResult secondAttempt = devUserResetService.deleteUser(
                "reset.qa@clubchretien.dev",
                null,
                UUID.randomUUID(),
                "qa-admin@clubchretien.dev"
        );

        assertNotNull(secondAttempt);
        assertFalse(secondAttempt.deleted());
    }

    @Test
    void deleteUser_shouldRemoveResidualAppUserAndAccountHolderWithSameFirebaseUidWhenDeletingByEmail() {
        UUID oldUserId = UUID.randomUUID();
        UUID residualAccountHolderId = UUID.randomUUID();
        String firebaseUid = "firebase-residual-" + oldUserId;
        OffsetDateTime now = OffsetDateTime.now();

        jdbcTemplate.update(
                "INSERT INTO users (id, firebase_uid, email, role, account_status, kyc_status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                oldUserId,
                firebaseUid,
                "residual.qa@clubchretien.dev",
                UserRole.USER.name(),
                AccountStatus.ACTIVE.name(),
                KycStatus.NOT_STARTED.name(),
                now,
                now
        );
        jdbcTemplate.update(
                "INSERT INTO account_holders (id, first_name, last_name, phone_number, country_code, service_country_code, " +
                        "account_type, created_via, kyc_status, kyc_level, created_at) " +
                        "VALUES (?, 'Residual', 'Holder', '22991000001', 'INT', 'INT', 'STANDARD', 'SELF', " +
                        "'NOT_STARTED', 'LEVEL_0_UNVERIFIED', ?)",
                residualAccountHolderId,
                now
        );
        jdbcTemplate.update(
                "INSERT INTO app_users (id, firebase_uid, account_holder_id, role, created_at) VALUES (?, ?, ?, 'USER', ?)",
                residualAccountHolderId,
                firebaseUid,
                residualAccountHolderId,
                now
        );

        DevUserResetService.DevUserResetResult result = devUserResetService.deleteUser(
                "residual.qa@clubchretien.dev",
                null,
                UUID.randomUUID(),
                "qa-admin@clubchretien.dev"
        );

        assertTrue(result.deleted());
        assertEquals(residualAccountHolderId, result.accountHolderId());
        assertEquals(0L, count("SELECT COUNT(*) FROM users WHERE id IN (?, ?)", oldUserId, residualAccountHolderId));
        assertEquals(0L, count("SELECT COUNT(*) FROM app_users WHERE firebase_uid = ?", firebaseUid));
        assertEquals(0L, count("SELECT COUNT(*) FROM account_holders WHERE id = ?", residualAccountHolderId));
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }
}
