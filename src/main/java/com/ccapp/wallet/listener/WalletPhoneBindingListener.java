package com.ccapp.wallet.listener;

import com.ccapp.common.events.IdempotentEventListenerExecutor;
import com.ccapp.progress.event.PhoneVerifiedEvent;
import com.ccapp.wallet.service.WalletActivationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tries wallet activation when a phone verification succeeds.
 *
 * <p>Wallet activation is now gated by completed identity onboarding plus a
 * verified phone number, so the listener simply re-checks eligibility when the
 * phone-verification event arrives.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WalletPhoneBindingListener {

    private final WalletActivationService walletActivationService;
    private final IdempotentEventListenerExecutor idempotentExecutor;

    @EventListener
    @Transactional
    public void onPhoneVerified(PhoneVerifiedEvent event) {
        idempotentExecutor.execute(
                "wallet-phone-binding-listener",
                event,
                () -> walletActivationService.activateIfEligible(event.accountHolderId())
        );
        log.info("wallet_phone_verification_event_processed accountHolderId={}", event.accountHolderId());
    }
}
