package com.ccapp.notifications.dispatcher;

import com.ccapp.notifications.domain.entity.Notification;
import com.ccapp.notifications.domain.enums.NotificationChannel;
import org.springframework.stereotype.Component;

@Component
public class InAppNotificationProvider implements NotificationChannelProvider {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.IN_APP;
    }

    @Override
    public DispatchResult send(Notification notification) {
        return new DispatchResult(true, "in-app:stored");
    }
}
