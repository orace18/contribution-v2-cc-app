package com.ccapp.notifications.dispatcher;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.notifications.domain.entity.Notification;
import com.ccapp.notifications.domain.enums.NotificationChannel;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationProvider implements NotificationChannelProvider {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public DispatchResult send(Notification notification) {
        if (notification.getMessage() == null || notification.getMessage().isBlank()) {
            throw new BusinessException("Email body cannot be empty");
        }
        return new DispatchResult(true, "email-provider:queued");
    }
}
