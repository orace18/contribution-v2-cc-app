package com.ccapp.notifications.dispatcher;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.notifications.domain.entity.Notification;
import com.ccapp.notifications.domain.enums.NotificationChannel;
import org.springframework.stereotype.Component;

@Component
public class SmsNotificationProvider implements NotificationChannelProvider {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.SMS;
    }

    @Override
    public DispatchResult send(Notification notification) {
        if (notification.getMessage() == null || notification.getMessage().isBlank()) {
            throw new BusinessException("SMS message cannot be empty");
        }
        return new DispatchResult(true, "sms-gateway:accepted");
    }
}
