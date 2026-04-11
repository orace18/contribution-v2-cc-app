package com.ccapp.wallet.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.financial.access.domain.FinancialAccessReasonCode;
import com.ccapp.identity.verification.domain.entity.PhoneVerification;
import com.ccapp.identity.verification.domain.enums.PhoneVerificationChannel;
import com.ccapp.identity.verification.domain.enums.PhoneVerificationStatus;
import com.ccapp.identity.verification.repository.PhoneVerificationRepository;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.identity.wallet.service.WalletService;
import com.ccapp.wallet.domain.enums.WalletType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletLifecycleServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID WALLET_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Mock
    private PhoneVerificationRepository phoneVerificationRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private WalletService walletService;

    @InjectMocks
    private WalletLifecycleService walletLifecycleService;

    @Test
    void resolveStatus_shouldExplainMissingPhoneVerificationWhenWalletIsAbsent() {
        when(walletRepository.findFirstByAccountHolderIdAndStatusOrderByCreatedAtDesc(ACCOUNT_ID, "ACTIVE"))
                .thenReturn(Optional.empty());
        when(phoneVerificationRepository.findTopByRequesterAccountHolderIdAndStatusOrderByVerifiedAtDescCreatedAtDesc(
                ACCOUNT_ID,
                PhoneVerificationStatus.VERIFIED
        )).thenReturn(Optional.empty());

        WalletStatusSnapshot snapshot = walletLifecycleService.resolveStatus(ACCOUNT_ID);

        assertNull(snapshot.walletId());
        assertFalse(snapshot.active());
        assertFalse(snapshot.phoneVerified());
        assertFalse(snapshot.activationEligible());
        assertEquals(WalletState.PHONE_NOT_VERIFIED, snapshot.state());
        assertEquals("Phone verification required for wallet activation", snapshot.message());
        assertEquals(List.of(FinancialAccessReasonCode.PHONE_NOT_VERIFIED), snapshot.blockingReasons());
    }

    @Test
    void resolveStatus_shouldReportWalletReadyForCreationWhenPhoneIsVerifiedAndWalletMissing() {
        when(walletRepository.findFirstByAccountHolderIdAndStatusOrderByCreatedAtDesc(ACCOUNT_ID, "ACTIVE"))
                .thenReturn(Optional.empty());
        when(phoneVerificationRepository.findTopByRequesterAccountHolderIdAndStatusOrderByVerifiedAtDescCreatedAtDesc(
                ACCOUNT_ID,
                PhoneVerificationStatus.VERIFIED
        )).thenReturn(Optional.of(verifiedPhone()));

        WalletStatusSnapshot snapshot = walletLifecycleService.resolveStatus(ACCOUNT_ID);

        assertNull(snapshot.walletId());
        assertFalse(snapshot.active());
        assertTrue(snapshot.phoneVerified());
        assertTrue(snapshot.activationEligible());
        assertEquals(WalletState.READY_FOR_WALLET_CREATION, snapshot.state());
        assertEquals("Wallet is ready for creation", snapshot.message());
        assertTrue(snapshot.blockingReasons().isEmpty());
    }

    @Test
    void activateIfEligible_shouldCreateWalletForVerifiedPhone() {
        when(phoneVerificationRepository.findTopByRequesterAccountHolderIdAndStatusOrderByVerifiedAtDescCreatedAtDesc(
                ACCOUNT_ID,
                PhoneVerificationStatus.VERIFIED
        )).thenReturn(Optional.of(verifiedPhone()));
        when(walletRepository.findByMobileMoneyNumber("22961000000")).thenReturn(Optional.empty());
        when(walletRepository.findFirstByAccountHolderIdAndStatusOrderByCreatedAtDesc(ACCOUNT_ID, "ACTIVE"))
                .thenReturn(Optional.empty());
        when(walletService.createWallet(eq(ACCOUNT_ID), eq("MOBILE"), eq("22961000000"), eq("ACTIVE")))
                .thenReturn(Wallet.builder()
                        .id(WALLET_ID)
                        .accountHolderId(ACCOUNT_ID)
                        .provider("MOBILE")
                        .mobileMoneyNumber("22961000000")
                        .status("ACTIVE")
                        .walletType(WalletType.USER)
                        .build());
        when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Wallet wallet = walletLifecycleService.activateIfEligible(ACCOUNT_ID).orElseThrow();

        assertEquals(ACCOUNT_ID, wallet.getAccountHolderId());
        assertEquals("22961000000", wallet.getMobileMoneyNumber());
        verify(walletService).createWallet(ACCOUNT_ID, "MOBILE", "22961000000", "ACTIVE");
    }

    @Test
    void activateIfEligible_shouldSkipWhenPhoneIsMissing() {
        when(phoneVerificationRepository.findTopByRequesterAccountHolderIdAndStatusOrderByVerifiedAtDescCreatedAtDesc(
                ACCOUNT_ID,
                PhoneVerificationStatus.VERIFIED
        )).thenReturn(Optional.empty());

        Optional<Wallet> wallet = walletLifecycleService.activateIfEligible(ACCOUNT_ID);

        assertTrue(wallet.isEmpty());
    }

    @Test
    void activate_shouldRejectWhenPhoneIsNotVerified() {
        when(phoneVerificationRepository.findTopByRequesterAccountHolderIdAndStatusOrderByVerifiedAtDescCreatedAtDesc(
                ACCOUNT_ID,
                PhoneVerificationStatus.VERIFIED
        )).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> walletLifecycleService.activate(ACCOUNT_ID));

        assertEquals(FinancialAccessReasonCode.PHONE_NOT_VERIFIED.name(), exception.getMessage());
    }

    private PhoneVerification verifiedPhone() {
        return PhoneVerification.builder()
                .id(UUID.randomUUID())
                .requesterAccountHolderId(ACCOUNT_ID)
                .phoneNumber("22961000000")
                .channel(PhoneVerificationChannel.SMS)
                .status(PhoneVerificationStatus.VERIFIED)
                .codeHash("hash")
                .attemptCount(0)
                .maxAttempts(5)
                .sendCount(1)
                .maxSendCount(3)
                .lastSentAt(OffsetDateTime.now())
                .resendAvailableAt(OffsetDateTime.now())
                .expiresAt(OffsetDateTime.now().plusMinutes(5))
                .verifiedAt(OffsetDateTime.now())
                .build();
    }

}
