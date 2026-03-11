package com.ccapp.notifications.service;

import com.ccapp.notifications.domain.enums.NotificationChannel;
import com.ccapp.notifications.domain.enums.NotificationType;
import com.ccapp.notifications.dto.CreateNotificationRequest;
import com.ccapp.notifications.event.NotificationRequestedEvent;
import com.ccapp.common.events.IdempotentEventListenerExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationRequestedListenerTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private IdempotentEventListenerExecutor listenerExecutor;

    @InjectMocks
    private NotificationRequestedListener listener;

    @Test
    void on_shouldCreateNotification() {
        NotificationRequestedEvent event = new NotificationRequestedEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                NotificationType.AGENT_ACTION,
                NotificationChannel.IN_APP,
                "AGENT_ACTION_ASSISTED_SESSION",
                "message",
                null,
                true
        );

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(2);
            runnable.run();
            return null;
        }).when(listenerExecutor).execute(anyString(), any(), any());

        listener.on(event);

        ArgumentCaptor<CreateNotificationRequest> requestCaptor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).createNotification(requestCaptor.capture());
        CreateNotificationRequest request = requestCaptor.getValue();
        assertEquals(event.accountHolderId(), request.accountHolderId());
        assertEquals(event.actorUserId(), request.actorUserId());
        assertEquals(event.type(), request.type());
        assertEquals(event.channel(), request.channel());
        assertEquals(event.templateCode(), request.templateCode());
        assertEquals(event.message(), request.message());
        assertEquals(event.sendNow(), request.sendNow());
    }

    @Test
    void on_shouldSwallowFailures() {
        NotificationRequestedEvent event = NotificationRequestedEvent.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                NotificationType.TASK_COMPLETED,
                NotificationChannel.IN_APP,
                "TASK_COMPLETED",
                "Task completed",
                null,
                true
        );
        doThrow(new RuntimeException("failure")).when(notificationService).createNotification(any());
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(2);
            runnable.run();
            return null;
        }).when(listenerExecutor).execute(anyString(), any(), any());

        assertDoesNotThrow(() -> listener.on(event));
    }
}
