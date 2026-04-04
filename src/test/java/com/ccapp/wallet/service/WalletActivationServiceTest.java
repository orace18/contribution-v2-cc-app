package com.ccapp.wallet.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.identity.domain.enums.OnboardingStep;
import com.ccapp.identity.dto.OnboardingStatusResponse;
import com.ccapp.identity.service.IdentityOnboardingService;
import com.ccapp.identity.verification.domain.entity.PhoneVerification;
import com.ccapp.identity.verification.domain.enums.PhoneVerificationChannel;
import com.ccapp.identity.verification.domain.enums.PhoneVerificationStatus;
import com.ccapp.identity.verification.repository.PhoneVerificationRepository;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.identity.wallet.service.WalletService;
import com.ccapp.mobile.onboarding.service.MobileOnboardingJourneyService;
import com.ccapp.wallet.domain.enums.WalletType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletActivationServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock
    private IdentityOnboardingService identityOnboardingService;
    @Mock
    private PhoneVerificationRepository phoneVerificationRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private WalletService walletService;
    @Mock
    private MobileOnboardingJourneyService mobileOnboardingJourneyService;

    @InjectMocks
    private WalletActivationService walletActivationService;

    @Test
    void activate_shouldRejectIncompleteIdentityOnboarding() {
        when(identityOnboardingService.getOnboardingStatus(ACCOUNT_ID)).thenReturn(identityIncomplete());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> walletActivationService.activate(ACCOUNT_ID));

        assertEquals("Identity onboarding incomplete", exception.getMessage());
        verify(walletService, never()).createWallet(any(), any(), any(), any());
        verify(mobileOnboardingJourneyService, never()).markWalletActivated(any());
    }

    @Test
    void activateIfEligible_shouldCreateWalletForVerifiedPhoneWithoutPlanSelection() {
        when(identityOnboardingService.getOnboardingStatus(ACCOUNT_ID)).thenReturn(identityCompleted());
        when(phoneVerificationRepository.findTopByRequesterAccountHolderIdAndStatusOrderByVerifiedAtDescCreatedAtDesc(
                ACCOUNT_ID,
                PhoneVerificationStatus.VERIFIED
        )).thenReturn(Optional.of(verifiedPhone("22961000000")));
        when(walletRepository.findByMobileMoneyNumber("22961000000")).thenReturn(Optional.empty());
        when(walletRepository.findFirstByAccountHolderIdAndStatusOrderByCreatedAtDesc(ACCOUNT_ID, "ACTIVE"))
                .thenReturn(Optional.empty());
        when(walletService.createWallet(eq(ACCOUNT_ID), eq("MOBILE"), eq("22961000000"), eq("ACTIVE")))
                .thenReturn(Wallet.builder()
                        .id(UUID.randomUUID())
                        .accountHolderId(ACCOUNT_ID)
                        .provider("MOBILE")
                        .mobileMoneyNumber("22961000000")
                        .status("ACTIVE")
                        .walletType(WalletType.USER)
                        .build());
        when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Wallet wallet = walletActivationService.activateIfEligible(ACCOUNT_ID).orElseThrow();

        assertEquals(ACCOUNT_ID, wallet.getAccountHolderId());
        assertEquals("22961000000", wallet.getMobileMoneyNumber());
        verify(mobileOnboardingJourneyService).markWalletActivated(ACCOUNT_ID);
    }

    private PhoneVerification verifiedPhone(String phoneNumber) {
        return PhoneVerification.builder()
                .id(UUID.randomUUID())
                .requesterAccountHolderId(ACCOUNT_ID)
                .phoneNumber(phoneNumber)
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

    private OnboardingStatusResponse identityCompleted() {
        return new OnboardingStatusResponse(
                ACCOUNT_ID,
                OnboardingStep.COMPLETED,
                3,
                3,
                new BigDecimal("100.00"),
                true,
                true,
                true,
                true,
                OffsetDateTime.now(),
                null
        );
    }

    private OnboardingStatusResponse identityIncomplete() {
        return new OnboardingStatusResponse(
                ACCOUNT_ID,
                OnboardingStep.STEP_2,
                2,
                3,
                new BigDecimal("66.67"),
                true,
                true,
                false,
                false,
                null,
                null
        );
    }
}
