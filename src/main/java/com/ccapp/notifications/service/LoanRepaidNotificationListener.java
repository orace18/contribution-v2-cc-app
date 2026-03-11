package com.ccapp.notifications.service;

import com.ccapp.common.events.DomainEventListener;
import com.ccapp.common.events.IdempotentEventListenerExecutor;
import com.ccapp.credit.event.LoanRepaidEvent;
import com.ccapp.notifications.domain.enums.NotificationChannel;
import com.ccapp.notifications.domain.enums.NotificationType;
import com.ccapp.notifications.dto.CreateNotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoanRepaidNotificationListener implements DomainEventListener<LoanRepaidEvent> {

    private static final String LISTENER_KEY = "notifications.loan-repaid";

    private final NotificationService notificationService;
    private final IdempotentEventListenerExecutor listenerExecutor;

    @Override
    @EventListener
    public void on(LoanRepaidEvent event) {
        try {
            listenerExecutor.execute(LISTENER_KEY, event, () -> notificationService.createNotification(new CreateNotificationRequest(
                    event.accountHolderId(),
                    event.actorUserId(),
                    NotificationType.LOAN_REPAID,
                    NotificationChannel.IN_APP,
                    "LOAN_REPAID",
                    "Your loan " + event.loanId() + " has been fully repaid.",
                    null,
                    true
            )));
        } catch (RuntimeException ex) {
            log.warn("Failed to emit LOAN_REPAID notification for loanId={}", event.loanId(), ex);
        }
    }
}
