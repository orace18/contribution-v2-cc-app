package com.ccapp.community.service;

import com.ccapp.common.events.IdempotentEventListenerExecutor;
import com.ccapp.community.event.PostCreatedEvent;
import com.ccapp.notifications.dto.CreateNotificationRequest;
import com.ccapp.notifications.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PostCreatedNotificationListenerTest {

    @Mock
    private IdempotentEventListenerExecutor listenerExecutor;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private PostCreatedNotificationListener listener;

    @Test
    void on_shouldEmitNotification() {
        PostCreatedEvent event = new PostCreatedEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "PUBLIC"
        );
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(2);
            runnable.run();
            return null;
        }).when(listenerExecutor).execute(anyString(), any(), any());

        listener.on(event);

        ArgumentCaptor<CreateNotificationRequest> requestCaptor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).createNotification(requestCaptor.capture());
        assertEquals(event.authorAccountHolderId(), requestCaptor.getValue().accountHolderId());
    }
}
