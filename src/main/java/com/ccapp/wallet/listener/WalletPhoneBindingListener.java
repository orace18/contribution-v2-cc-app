package com.ccapp.wallet.listener;

import com.ccapp.common.events.IdempotentEventListenerExecutor;
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
        // Phone uniqueness: if another account already owns this wallet, warn and abort
        if (walletRepository.findByMobileMoneyNumber(phoneNumber).isPresent()) {
            log.warn("wallet_phone_binding_skip_duplicate phone={} accountHolderId={}",
                    phoneNumber, accountHolderId);
            return;
        }

        // Idempotency: account holder may already have an active wallet
        if (walletRepository.findFirstByAccountHolderIdAndStatusOrderByCreatedAtDesc(
                accountHolderId, "ACTIVE").isPresent()) {
            log.info("wallet_phone_binding_skip_exists accountHolderId={}", accountHolderId);
            return;
        }

        Wallet wallet = walletRepository.save(Wallet.builder()
                .accountHolderId(accountHolderId)
                .provider("MOBILE")
                .mobileMoneyNumber(phoneNumber)
                .status("ACTIVE")
                .walletType(WalletType.USER)
                .build());

        log.info("wallet_created_on_phone_verification walletId={} accountHolderId={} phone={}",
                wallet.getId(), accountHolderId, phoneNumber);
    }
}
