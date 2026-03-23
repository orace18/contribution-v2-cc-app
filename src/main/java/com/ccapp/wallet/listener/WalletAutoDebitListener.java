package com.ccapp.wallet.listener;

import com.ccapp.common.events.IdempotentEventListenerExecutor;
import com.ccapp.wallet.event.WalletCreditedEvent;
import com.ccapp.wallet.service.WalletAutoDebitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triggers the auto-debit rule engine whenever a wallet is credited.
 *
 * <p>Uses idempotent execution to ensure exactly-once processing per credit event.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WalletAutoDebitListener {

    private final WalletAutoDebitService autoDebitService;
    private final IdempotentEventListenerExecutor idempotentExecutor;

    @EventListener
    @Transactional
    public void onWalletCredited(WalletCreditedEvent event) {
        idempotentExecutor.execute(
                "wallet-auto-debit-listener",
                event,
                () -> {
                    log.debug("wallet_auto_debit_trigger walletId={} operationId={} amount={}",
                            event.walletId(), event.operationId(), event.amount());
                    autoDebitService.executeAutoDebits(
                            event.walletId(),
                            event.amount(),
                            event.currency(),
                            event.operationId());
                }
        );
    }
}
