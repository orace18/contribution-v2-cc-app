package com.ccapp.notifications.dispatcher;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.notifications.domain.entity.Notification;
import com.ccapp.notifications.domain.enums.NotificationChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SmsNotificationProvider implements NotificationChannelProvider {

    private final PhoneMessageGateway phoneMessageGateway;
    private final AccountHolderService accountHolderService;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.SMS;
    }

    @Override
    public DispatchResult send(Notification notification) {
        if (notification.getMessage() == null || notification.getMessage().isBlank()) {
            throw new BusinessException("SMS message cannot be empty");
        }

        AccountHolder accountHolder = accountHolderService.getById(notification.getAccountHolderId());
        String phoneNumber = accountHolder.getPhoneNumber() == null ? null : accountHolder.getPhoneNumber().trim();
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new BusinessException("SMS_PHONE_REQUIRED");
        }

        PhoneMessageDispatchResult result = phoneMessageGateway.sendSms(phoneNumber, notification.getMessage().trim());
        return new DispatchResult(result.success(), result.providerResponse());
    }
}
