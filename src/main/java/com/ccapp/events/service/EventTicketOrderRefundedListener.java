package com.ccapp.events.service;

import com.ccapp.common.events.DomainEventListener;
import com.ccapp.common.events.IdempotentEventListenerExecutor;
import com.ccapp.shop.event.ShopOrderRefundedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventTicketOrderRefundedListener implements DomainEventListener<ShopOrderRefundedEvent> {

    private static final String LISTENER_KEY = "events.ticket-order-refunded";

    private final IdempotentEventListenerExecutor listenerExecutor;
    private final EventTicketFulfillmentService eventTicketFulfillmentService;

    @Override
    @EventListener
    public void on(ShopOrderRefundedEvent event) {
        listenerExecutor.execute(
                LISTENER_KEY,
                event,
                () -> eventTicketFulfillmentService.markRefundedOrder(event.orderId())
        );
    }
}
