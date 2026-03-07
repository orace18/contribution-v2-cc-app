package com.ccapp.notifications.dispatcher;

import com.ccapp.notifications.domain.entity.Notification;
import com.ccapp.notifications.domain.enums.NotificationChannel;

public interface NotificationChannelProvider {

    NotificationChannel channel();

    DispatchResult send(Notification notification);
}
