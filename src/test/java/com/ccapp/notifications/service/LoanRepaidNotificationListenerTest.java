package com.ccapp.notifications.service;

import com.ccapp.credit.event.LoanRepaidEvent;
import com.ccapp.common.events.IdempotentEventListenerExecutor;
import com.ccapp.notifications.domain.enums.NotificationChannel;
import com.ccapp.notifications.domain.enums.NotificationType;
import com.ccapp.notifications.dto.CreateNotificationRequest;
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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LoanRepaidNotificationListenerTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private IdempotentEventListenerExecutor listenerExecutor;

    @InjectMocks
    private LoanRepaidNotificationListener listener;

    @Test
    void on_shouldCreateLoanRepaidNotification() {
        LoanRepaidEvent event = new LoanRepaidEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("2000"),
                "XOF"
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
        assertEquals(NotificationType.LOAN_REPAID, request.type());
        assertEquals(NotificationChannel.IN_APP, request.channel());
        assertEquals("LOAN_REPAID", request.templateCode());
        assertEquals("Your loan " + event.loanId() + " has been fully repaid.", request.message());
    }

    @Test
    void on_shouldSwallowNotificationFailures() {
        LoanRepaidEvent event = new LoanRepaidEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("2000"),
                "XOF"
        );
        doThrow(new RuntimeException("notification failure")).when(notificationService).createNotification(any());
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(2);
            runnable.run();
            return null;
        }).when(listenerExecutor).execute(anyString(), any(), any());

        assertDoesNotThrow(() -> listener.on(event));
    }
}
