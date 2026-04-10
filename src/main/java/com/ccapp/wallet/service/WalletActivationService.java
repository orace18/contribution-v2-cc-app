package com.ccapp.wallet.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.financial.access.domain.FinancialAccessDecision;
import com.ccapp.financial.access.domain.FinancialAccessUseCase;
import com.ccapp.financial.access.service.FinancialAccessPolicy;
import com.ccapp.identity.verification.domain.enums.PhoneVerificationStatus;
import com.ccapp.identity.verification.repository.PhoneVerificationRepository;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.identity.wallet.service.WalletService;
import com.ccapp.mobile.onboarding.service.MobileOnboardingJourneyService;
import com.ccapp.wallet.domain.enums.WalletType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletActivationService {

    private final FinancialAccessPolicy financialAccessPolicy;
    private final PhoneVerificationRepository phoneVerificationRepository;
    private final WalletRepository walletRepository;
    private final WalletService walletService;
    private final MobileOnboardingJourneyService mobileOnboardingJourneyService;

    @Transactional
    public Wallet activate(UUID accountHolderId) {
        financialAccessPolicy.assertAllowed(accountHolderId, FinancialAccessUseCase.WALLET_ACTIVATION);

        String verifiedPhoneNumber = findVerifiedPhoneNumber(accountHolderId)
                .orElseThrow(() -> new BusinessException("Phone verification required"));

        return activateWithVerifiedPhone(accountHolderId, verifiedPhoneNumber);
    }

    @Transactional
    public Optional<Wallet> activateIfEligible(UUID accountHolderId) {
        FinancialAccessDecision decision = financialAccessPolicy.evaluate(accountHolderId, FinancialAccessUseCase.WALLET_ACTIVATION);
        if (!decision.allowed()) {
            log.info("wallet_activation_skipped accountHolderId={} reason=financial_access_denied reasonCodes={} message={}",
                    accountHolderId,
                    decision.reasonCodes(),
                    decision.primaryMessage());
            return Optional.empty();
        }

        Optional<String> verifiedPhoneNumber = findVerifiedPhoneNumber(accountHolderId);
        if (verifiedPhoneNumber.isEmpty()) {
            log.info("wallet_activation_skipped accountHolderId={} reason=phone_not_verified", accountHolderId);
            return Optional.empty();
        }

        return verifiedPhoneNumber
                .map(phoneNumber -> activateWithVerifiedPhone(accountHolderId, phoneNumber));
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
                log.info("wallet_activation_bound_phone accountHolderId={} walletId={} phone={}",
                        accountHolderId,
                        resolvedWallet.getId(),
                        normalizedPhone);
            }
        } else {
            if (walletByPhone != null) {
                throw new BusinessException("WALLET_PHONE_BINDING_REQUIRES_MANUAL_REVIEW");
            }
            resolvedWallet = walletService.createWallet(accountHolderId, "MOBILE", normalizedPhone, "ACTIVE");
            resolvedWallet.setWalletType(WalletType.USER);
            resolvedWallet = walletRepository.save(resolvedWallet);
            log.info("wallet_activation_created accountHolderId={} walletId={} phone={}",
                    accountHolderId,
                    resolvedWallet.getId(),
                    normalizedPhone);
        }

        mobileOnboardingJourneyService.markWalletActivated(accountHolderId);
        log.info("wallet_activation_completed accountHolderId={} walletId={}",
                accountHolderId,
                resolvedWallet.getId());
        return resolvedWallet;
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
