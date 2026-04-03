package com.ccapp.events.service;

import com.ccapp.common.events.DomainEventListener;
import com.ccapp.common.events.IdempotentEventListenerExecutor;
import com.ccapp.shop.event.ShopOrderPaidEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventTicketOrderPaidListener implements DomainEventListener<ShopOrderPaidEvent> {

    private static final String LISTENER_KEY = "events.ticket-order-paid";

    private final IdempotentEventListenerExecutor listenerExecutor;
    private final EventTicketFulfillmentService eventTicketFulfillmentService;

    @Override
    @EventListener
    public void on(ShopOrderPaidEvent event) {
        listenerExecutor.execute(
                LISTENER_KEY,
                event,
                () -> eventTicketFulfillmentService.fulfillPaidOrder(event.orderId(), event.buyerId())
        );
    }
}
