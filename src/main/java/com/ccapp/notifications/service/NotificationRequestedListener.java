package com.ccapp.notifications.service;

import com.ccapp.common.events.DomainEventListener;
import com.ccapp.common.events.IdempotentEventListenerExecutor;
import com.ccapp.notifications.dto.CreateNotificationRequest;
import com.ccapp.notifications.event.NotificationRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationRequestedListener implements DomainEventListener<NotificationRequestedEvent> {

    private static final String LISTENER_KEY = "notifications.notification-requested";

    private final NotificationService notificationService;
    private final IdempotentEventListenerExecutor listenerExecutor;

    @Override
    @EventListener
    public void on(NotificationRequestedEvent event) {
        try {
            listenerExecutor.execute(LISTENER_KEY, event, () -> notificationService.createNotification(new CreateNotificationRequest(
                    event.accountHolderId(),
                    event.actorUserId(),
                    event.type(),
                    event.channel(),
                    event.templateCode(),
                    null,
                    event.message(),
                    event.variables(),
                    event.targetId(),
                    event.deepLink(),
                    event.sendNow()
            )));
        } catch (RuntimeException ex) {
            log.warn(
                    "Failed to handle NotificationRequestedEvent type={} accountHolderId={}",
                    event.type(),
                    event.accountHolderId(),
                    ex
            );
        }
    }
}
