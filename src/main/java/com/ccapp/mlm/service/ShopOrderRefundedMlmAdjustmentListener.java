package com.ccapp.mlm.service;

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
public class ShopOrderRefundedMlmAdjustmentListener implements DomainEventListener<ShopOrderRefundedEvent> {

    private static final String LISTENER_KEY = "mlm.shop-order-refunded-adjustment";

    private final IdempotentEventListenerExecutor listenerExecutor;

    @Override
    @EventListener
    public void on(ShopOrderRefundedEvent event) {
        try {
            listenerExecutor.execute(LISTENER_KEY, event, () ->
                    log.info("shop_order_refund_legacy_mlm_listener_skipped orderId={}", event.orderId())
            );
        } catch (RuntimeException ex) {
            log.warn("shop_order_refund_adjustment_failed orderId={}", event.orderId(), ex);
        }
    }
}
