package com.ccapp.notifications.dispatcher;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.notifications.domain.entity.Notification;
import com.ccapp.notifications.domain.enums.NotificationChannel;
import com.ccapp.notifications.service.PushService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PushNotificationProvider implements NotificationChannelProvider {

    private final PushService pushService;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.PUSH;
    }

    @Override
    public DispatchResult send(Notification notification) {
        if (notification.getMessage() == null || notification.getMessage().isBlank()) {
            throw new BusinessException("Push payload cannot be empty");
        }
        String providerResponse = pushService.sendToAccountHolder(
                notification.getAccountHolderId(),
                notification.getType().name(),
                notification.getMessage().trim(),
                java.util.Map.of(
                        "notificationId", notification.getId() == null ? "" : notification.getId().toString(),
                        "notificationType", notification.getType().name(),
                        "accountHolderId", notification.getAccountHolderId().toString()
                )
        );
        return new DispatchResult(true, providerResponse);
    }
}
