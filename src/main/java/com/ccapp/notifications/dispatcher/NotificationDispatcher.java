package com.ccapp.notifications.dispatcher;

import com.ccapp.common.exception.NotFoundException;
import com.ccapp.notifications.domain.entity.Notification;
import com.ccapp.notifications.domain.enums.NotificationChannel;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class NotificationDispatcher {

    private final Map<NotificationChannel, NotificationChannelProvider> providers;

    public NotificationDispatcher(List<NotificationChannelProvider> providerList) {
        this.providers = new EnumMap<>(NotificationChannel.class);
        for (NotificationChannelProvider provider : providerList) {
            this.providers.put(provider.channel(), provider);
        }
    }

    public DispatchResult dispatch(Notification notification) {
        NotificationChannelProvider provider = providers.get(notification.getChannel());
        if (provider == null) {
            throw new NotFoundException("Notification provider not found for channel " + notification.getChannel());
        }
        return provider.send(notification);
    }
}
