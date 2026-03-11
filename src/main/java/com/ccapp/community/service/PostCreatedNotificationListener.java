package com.ccapp.community.service;

import com.ccapp.common.events.DomainEventListener;
import com.ccapp.common.events.IdempotentEventListenerExecutor;
import com.ccapp.community.event.PostCreatedEvent;
import com.ccapp.notifications.domain.enums.NotificationChannel;
import com.ccapp.notifications.domain.enums.NotificationType;
import com.ccapp.notifications.dto.CreateNotificationRequest;
import com.ccapp.notifications.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PostCreatedNotificationListener implements DomainEventListener<PostCreatedEvent> {

    private static final String LISTENER_KEY = "community.post-created-notification";

    private final IdempotentEventListenerExecutor listenerExecutor;
    private final NotificationService notificationService;

    @Override
    @EventListener
    public void on(PostCreatedEvent event) {
        try {
            listenerExecutor.execute(LISTENER_KEY, event, () -> {
                if (event.authorAccountHolderId() == null) {
                    return;
                }
                notificationService.createNotification(new CreateNotificationRequest(
                        event.authorAccountHolderId(),
                        null,
                        NotificationType.AGENT_ACTION,
                        NotificationChannel.IN_APP,
                        "COMMUNITY_POST_CREATED",
                        "Your community post has been published.",
                        null,
                        true
                ));
            });
        } catch (RuntimeException ex) {
            log.warn("Failed to handle PostCreatedEvent postId={}", event.postId(), ex);
        }
    }
}
