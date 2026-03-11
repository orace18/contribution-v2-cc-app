package com.ccapp.payments.commission.service;

import com.ccapp.common.events.IdempotentEventListenerExecutor;
import com.ccapp.notifications.dto.CreateNotificationRequest;
import com.ccapp.notifications.service.NotificationService;
import com.ccapp.payments.event.CommissionGeneratedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CommissionGeneratedNotificationListenerTest {

    @Mock
    private IdempotentEventListenerExecutor listenerExecutor;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private CommissionGeneratedNotificationListener listener;

    @Test
    void on_shouldEmitNotification() {
        CommissionGeneratedEvent event = new CommissionGeneratedEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("500"),
                "XOF",
                "SUBSCRIPTION"
        );
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(2);
            runnable.run();
            return null;
        }).when(listenerExecutor).execute(anyString(), any(), any());

        listener.on(event);

        ArgumentCaptor<CreateNotificationRequest> requestCaptor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).createNotification(requestCaptor.capture());
        assertEquals(event.beneficiaryAccountHolderId(), requestCaptor.getValue().accountHolderId());
    }
}
