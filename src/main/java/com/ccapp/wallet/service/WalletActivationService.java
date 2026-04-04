package com.ccapp.wallet.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.identity.dto.OnboardingStatusResponse;
import com.ccapp.identity.service.IdentityOnboardingService;
import com.ccapp.identity.verification.domain.enums.PhoneVerificationStatus;
import com.ccapp.identity.verification.repository.PhoneVerificationRepository;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.identity.wallet.service.WalletService;
import com.ccapp.mobile.onboarding.service.MobileOnboardingJourneyService;
import com.ccapp.wallet.domain.enums.WalletType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletActivationService {

    private final IdentityOnboardingService identityOnboardingService;
    private final PhoneVerificationRepository phoneVerificationRepository;
    private final WalletRepository walletRepository;
    private final WalletService walletService;
    private final MobileOnboardingJourneyService mobileOnboardingJourneyService;

    @Transactional
    public Wallet activate(UUID accountHolderId) {
        if (!isIdentityOnboardingCompleted(accountHolderId)) {
            throw new BusinessException("Identity onboarding incomplete");
        }

        String verifiedPhoneNumber = findVerifiedPhoneNumber(accountHolderId)
                .orElseThrow(() -> new BusinessException("Phone verification required"));

        return activateWithVerifiedPhone(accountHolderId, verifiedPhoneNumber);
    }

    @Transactional
    public Optional<Wallet> activateIfEligible(UUID accountHolderId) {
        if (!isIdentityOnboardingCompleted(accountHolderId)) {
            return Optional.empty();
        }

        return findVerifiedPhoneNumber(accountHolderId)
                .map(verifiedPhoneNumber -> activateWithVerifiedPhone(accountHolderId, verifiedPhoneNumber));
    }

    private Wallet activateWithVerifiedPhone(UUID accountHolderId, String verifiedPhoneNumber) {
        String normalizedPhone = verifiedPhoneNumber.trim();
        Wallet walletByPhone = walletRepository.findByMobileMoneyNumber(normalizedPhone).orElse(null);
        Wallet activeWallet = walletRepository.findFirstByAccountHolderIdAndStatusOrderByCreatedAtDesc(
                accountHolderId,
                "ACTIVE"
        ).orElse(null);

        if (walletByPhone != null && !walletByPhone.getAccountHolderId().equals(accountHolderId)) {
            throw new BusinessException("PHONE_ALREADY_BOUND_TO_ANOTHER_WALLET");
        }

        Wallet resolvedWallet = activeWallet;
        if (resolvedWallet != null) {
            if (resolvedWallet.getMobileMoneyNumber() == null
                    || resolvedWallet.getMobileMoneyNumber().isBlank()
                    || !resolvedWallet.getMobileMoneyNumber().trim().equals(normalizedPhone)) {
                resolvedWallet.setMobileMoneyNumber(normalizedPhone);
                resolvedWallet = walletRepository.save(resolvedWallet);
            }
        } else {
            if (walletByPhone != null) {
                throw new BusinessException("WALLET_PHONE_BINDING_REQUIRES_MANUAL_REVIEW");
            }
            resolvedWallet = walletService.createWallet(accountHolderId, "MOBILE", normalizedPhone, "ACTIVE");
            resolvedWallet.setWalletType(WalletType.USER);
            resolvedWallet = walletRepository.save(resolvedWallet);
        }

        mobileOnboardingJourneyService.markWalletActivated(accountHolderId);
        return resolvedWallet;
    }

    private boolean isIdentityOnboardingCompleted(UUID accountHolderId) {
        OnboardingStatusResponse identityStatus = identityOnboardingService.getOnboardingStatus(accountHolderId);
        return identityStatus != null && identityStatus.onboardingCompleted();
    }

    private Optional<String> findVerifiedPhoneNumber(UUID accountHolderId) {
        return phoneVerificationRepository
                .findTopByRequesterAccountHolderIdAndStatusOrderByVerifiedAtDescCreatedAtDesc(
                        accountHolderId,
                        PhoneVerificationStatus.VERIFIED
                )
                .map(verification -> verification.getPhoneNumber() == null ? null : verification.getPhoneNumber().trim())
                .filter(phoneNumber -> phoneNumber != null && !phoneNumber.isBlank());
    }
}
