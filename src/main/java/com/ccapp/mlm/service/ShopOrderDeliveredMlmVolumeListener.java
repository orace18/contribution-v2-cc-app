package com.ccapp.mlm.service;

import com.ccapp.common.events.DomainEventListener;
import com.ccapp.common.events.IdempotentEventListenerExecutor;
import com.ccapp.shop.event.ShopOrderDeliveredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShopOrderDeliveredMlmVolumeListener implements DomainEventListener<ShopOrderDeliveredEvent> {

    private static final String LISTENER_KEY = "mlm.shop-order-delivered-volume-ledger";

    private final IdempotentEventListenerExecutor listenerExecutor;
    private final MlmVolumeLedgerService mlmVolumeLedgerService;

    @Override
    @EventListener
    public void on(ShopOrderDeliveredEvent event) {
        try {
            listenerExecutor.execute(LISTENER_KEY, event, () -> mlmVolumeLedgerService.recordForDeliveredOrder(event));
        } catch (RuntimeException ex) {
            log.warn("Failed to write MLM volume ledger for delivered orderId={}", event.orderId(), ex);
        }
    }
}
