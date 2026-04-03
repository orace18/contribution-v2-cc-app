package com.ccapp.commerce.service;

import com.ccapp.common.events.DomainEventListener;
import com.ccapp.common.events.IdempotentEventListenerExecutor;
import com.ccapp.shop.event.ShopOrderRefundedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommerceRefundListener implements DomainEventListener<ShopOrderRefundedEvent> {

    private static final String LISTENER_KEY = "commerce.shop-order-refunded-dsb-reversal";

    private final IdempotentEventListenerExecutor listenerExecutor;
    private final CommerceSettlementService commerceSettlementService;

    @Override
    @EventListener
    public void on(ShopOrderRefundedEvent event) {
        try {
            listenerExecutor.execute(LISTENER_KEY, event,
                    () -> commerceSettlementService.reverseRefundedOrder(event.orderId()));
        } catch (RuntimeException ex) {
            log.warn("commerce_refund_reversal_failed orderId={}", event.orderId(), ex);
        }
    }
}
