package com.ccapp.wallet.listener;

import com.ccapp.common.events.IdempotentEventListenerExecutor;
import com.ccapp.mobile.onboarding.service.MobileOnboardingJourneyService;
import com.ccapp.progress.event.PhoneVerifiedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relays verified-phone events to the onboarding journey.
 *
 * <p>The onboarding state machine now decides whether wallet activation may
 * happen. Phone verification alone is no longer enough to provision a wallet
 * during early onboarding.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WalletPhoneBindingListener {

    private final MobileOnboardingJourneyService mobileOnboardingJourneyService;
    private final IdempotentEventListenerExecutor idempotentExecutor;

    @EventListener
    @Transactional
    public void onPhoneVerified(PhoneVerifiedEvent event) {
        idempotentExecutor.execute(
                "wallet-phone-binding-listener",
                event,
                () -> mobileOnboardingJourneyService.onPhoneVerified(
                        event.accountHolderId(),
                        event.phoneNumber()
                )
        );
        log.info("wallet_phone_verification_event_processed accountHolderId={}", event.accountHolderId());
    }
}
