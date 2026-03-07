package com.ccapp.notifications.dispatcher;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.notifications.domain.entity.Notification;
import com.ccapp.notifications.domain.enums.NotificationChannel;
import org.springframework.stereotype.Component;

@Component
public class PushNotificationProvider implements NotificationChannelProvider {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.PUSH;
    }

    @Override
    public DispatchResult send(Notification notification) {
        if (notification.getMessage() == null || notification.getMessage().isBlank()) {
            throw new BusinessException("Push payload cannot be empty");
        }
        return new DispatchResult(true, "firebase:queued");
    }
}
