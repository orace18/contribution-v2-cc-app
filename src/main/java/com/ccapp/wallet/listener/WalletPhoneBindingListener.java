package com.ccapp.wallet.listener;

import com.ccapp.common.events.IdempotentEventListenerExecutor;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.identity.wallet.service.WalletService;
import com.ccapp.mobile.onboarding.service.MobileOnboardingJourneyService;
import com.ccapp.progress.event.PhoneVerifiedEvent;
import com.ccapp.wallet.domain.enums.WalletType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Binds a verified phone number to a wallet (1 phone = 1 wallet rule).
 *
 * <p>Listens to {@link PhoneVerifiedEvent} and auto-creates the wallet metadata
 * if neither a wallet for that phone nor one for that account holder already exists.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WalletPhoneBindingListener {

    private final WalletRepository walletRepository;
    private final WalletService walletService;
    private final MobileOnboardingJourneyService mobileOnboardingJourneyService;
    private final IdempotentEventListenerExecutor idempotentExecutor;

    @EventListener
    @Transactional
    public void onPhoneVerified(PhoneVerifiedEvent event) {
        idempotentExecutor.execute(
                "wallet-phone-binding-listener",
                event,
                () -> bindPhone(event.accountHolderId(), event.phoneNumber())
        );
    }

    private void bindPhone(UUID accountHolderId, String phoneNumber) {
        String normalizedPhone = phoneNumber == null ? null : phoneNumber.trim();
        Wallet walletByPhone = normalizedPhone == null || normalizedPhone.isBlank()
                ? null
                : walletRepository.findByMobileMoneyNumber(normalizedPhone).orElse(null);
        Wallet activeWallet = walletRepository.findFirstByAccountHolderIdAndStatusOrderByCreatedAtDesc(
                accountHolderId, "ACTIVE").orElse(null);

        if (walletByPhone != null && !walletByPhone.getAccountHolderId().equals(accountHolderId)) {
            throw new BusinessException("PHONE_ALREADY_BOUND_TO_ANOTHER_WALLET");
        }

        if (activeWallet != null) {
            if (activeWallet.getMobileMoneyNumber() == null || activeWallet.getMobileMoneyNumber().isBlank()
                    || !activeWallet.getMobileMoneyNumber().trim().equals(normalizedPhone)) {
                activeWallet.setMobileMoneyNumber(normalizedPhone);
                walletRepository.save(activeWallet);
                mobileOnboardingJourneyService.markWalletActivated(accountHolderId);
                log.info("wallet_phone_binding_reconciled walletId={} accountHolderId={} phone={}",
                        activeWallet.getId(), accountHolderId, maskPhone(normalizedPhone));
                return;
            }
            mobileOnboardingJourneyService.markWalletActivated(accountHolderId);
            log.info("wallet_phone_binding_verified walletId={} accountHolderId={} phone={}",
                    activeWallet.getId(), accountHolderId, maskPhone(normalizedPhone));
            return;
        }

        if (walletByPhone != null) {
            throw new BusinessException("WALLET_PHONE_BINDING_REQUIRES_MANUAL_REVIEW");
        }

        Wallet wallet = walletService.createWallet(accountHolderId, "MOBILE", normalizedPhone, "ACTIVE");
        wallet.setWalletType(WalletType.USER);
        walletRepository.save(wallet);
        mobileOnboardingJourneyService.markWalletActivated(accountHolderId);

        log.info("wallet_created_on_phone_verification walletId={} accountHolderId={} phone={}",
                wallet.getId(), accountHolderId, maskPhone(phoneNumber));
    }

    private String maskPhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "***";
        }
        int keep = Math.min(4, phoneNumber.length());
        return "*".repeat(phoneNumber.length() - keep) + phoneNumber.substring(phoneNumber.length() - keep);
    }
}
