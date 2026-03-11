package com.ccapp.notifications.service;

import com.ccapp.common.events.DomainEventListener;
import com.ccapp.common.events.IdempotentEventListenerExecutor;
import com.ccapp.notifications.domain.enums.NotificationChannel;
import com.ccapp.notifications.domain.enums.NotificationType;
import com.ccapp.notifications.dto.CreateNotificationRequest;
import com.ccapp.notifications.event.MessageSentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessageSentNotificationListener implements DomainEventListener<MessageSentEvent> {

    private static final String LISTENER_KEY = "notifications.message-sent-notification";

    private final IdempotentEventListenerExecutor listenerExecutor;
    private final NotificationService notificationService;

    @Override
    @EventListener
    public void on(MessageSentEvent event) {
        try {
            listenerExecutor.execute(LISTENER_KEY, event, () -> notificationService.createNotification(new CreateNotificationRequest(
                    event.recipientAccountHolderId(),
                    null,
                    NotificationType.AGENT_ACTION,
                    NotificationChannel.IN_APP,
                    "CHAT_MESSAGE_RECEIVED",
                    "You received a new chat message.",
                    null,
                    true
            )));
        } catch (RuntimeException ex) {
            log.warn("Failed to handle MessageSentEvent messageId={}", event.messageId(), ex);
        }
    }
}
