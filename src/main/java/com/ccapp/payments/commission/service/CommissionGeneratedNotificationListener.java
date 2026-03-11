package com.ccapp.payments.commission.service;

import com.ccapp.common.events.DomainEventListener;
import com.ccapp.common.events.IdempotentEventListenerExecutor;
import com.ccapp.notifications.domain.enums.NotificationChannel;
import com.ccapp.notifications.domain.enums.NotificationType;
import com.ccapp.notifications.dto.CreateNotificationRequest;
import com.ccapp.notifications.service.NotificationService;
import com.ccapp.payments.event.CommissionGeneratedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommissionGeneratedNotificationListener implements DomainEventListener<CommissionGeneratedEvent> {

    private static final String LISTENER_KEY = "payments.commission-generated-notification";

    private final IdempotentEventListenerExecutor listenerExecutor;
    private final NotificationService notificationService;

    @Override
    @EventListener
    public void on(CommissionGeneratedEvent event) {
        try {
            listenerExecutor.execute(LISTENER_KEY, event, () -> notificationService.createNotification(new CreateNotificationRequest(
                    event.beneficiaryAccountHolderId(),
                    null,
                    NotificationType.AGENT_ACTION,
                    NotificationChannel.IN_APP,
                    "MLM_COMMISSION_GENERATED",
                    "Commission generated: " + event.amount() + " " + event.currency(),
                    null,
                    true
            )));
        } catch (RuntimeException ex) {
            log.warn("Failed to handle CommissionGeneratedEvent operationId={}", event.operationId(), ex);
        }
    }
}
