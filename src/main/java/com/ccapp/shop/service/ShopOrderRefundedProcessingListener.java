package com.ccapp.shop.service;

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
public class ShopOrderRefundedProcessingListener implements DomainEventListener<ShopOrderRefundedEvent> {

    private static final String LISTENER_KEY = "shop.shop-order-refunded-processing";

    private final IdempotentEventListenerExecutor listenerExecutor;
    private final ShopOrderRefundService shopOrderRefundService;

    @Override
    @EventListener
    public void on(ShopOrderRefundedEvent event) {
        try {
            listenerExecutor.execute(LISTENER_KEY, event, () -> shopOrderRefundService.processRefund(event));
        } catch (RuntimeException ex) {
            log.warn("shop_order_refund_processing_failed orderId={}", event.orderId(), ex);
        }
    }
}
