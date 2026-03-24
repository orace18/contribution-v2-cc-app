package com.ccapp.wallet.listener;

import com.ccapp.common.events.IdempotentEventListenerExecutor;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
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
                log.info("wallet_phone_binding_reconciled walletId={} accountHolderId={} phone={}",
                        activeWallet.getId(), accountHolderId, normalizedPhone);
                return;
            }
            log.info("wallet_phone_binding_verified walletId={} accountHolderId={} phone={}",
                    activeWallet.getId(), accountHolderId, normalizedPhone);
            return;
        }

        if (walletByPhone != null) {
            throw new BusinessException("WALLET_PHONE_BINDING_REQUIRES_MANUAL_REVIEW");
        }

        Wallet wallet = walletRepository.save(Wallet.builder()
                .accountHolderId(accountHolderId)
                .provider("MOBILE")
                .mobileMoneyNumber(normalizedPhone)
                .status("ACTIVE")
                .walletType(WalletType.USER)
                .build());

        log.info("wallet_created_on_phone_verification walletId={} accountHolderId={} phone={}",
                wallet.getId(), accountHolderId, phoneNumber);
    }
}
