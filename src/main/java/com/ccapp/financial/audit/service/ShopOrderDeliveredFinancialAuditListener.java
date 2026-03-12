package com.ccapp.financial.audit.service;

import com.ccapp.common.events.DomainEventListener;
import com.ccapp.common.events.IdempotentEventListenerExecutor;
import com.ccapp.shop.event.ShopOrderDeliveredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShopOrderDeliveredFinancialAuditListener implements DomainEventListener<ShopOrderDeliveredEvent> {

    private static final String LISTENER_KEY = "financial.shop-order-delivered-audit";

    private final IdempotentEventListenerExecutor listenerExecutor;
    private final FinancialAuditService financialAuditService;

    @Override
    @EventListener
    public void on(ShopOrderDeliveredEvent event) {
        try {
            listenerExecutor.execute(LISTENER_KEY, event, () -> financialAuditService.record(
                    "MERCHANT_PAYOUT",
                    event.orderId().toString(),
                    null,
                    event.merchantAmount(),
                    Map.of(
                            "buyerId", String.valueOf(event.buyerId()),
                            "currency", String.valueOf(event.currency()),
                            "platformCommission", String.valueOf(event.platformCommission()),
                            "mlmVolume", String.valueOf(event.mlmVolume())
                    )
            ));
        } catch (RuntimeException ex) {
            log.warn("financial_audit_merchant_payout_failed orderId={}", event.orderId(), ex);
        }
    }
}
