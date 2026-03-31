package com.ccapp.notifications.dispatcher;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "messaging.africastalking.enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class LoggingPhoneMessageGateway implements PhoneMessageGateway {

    @Override
    public PhoneMessageDispatchResult sendSms(String toPhoneNumber, String message) {
        log.info("PhoneMessageGateway fallback SMS to={} messageLength={}", maskPhone(toPhoneNumber), messageLength(message));
        return new PhoneMessageDispatchResult(true, "fallback:sms:accepted");
    }

    @Override
    public PhoneMessageDispatchResult sendWhatsApp(String toPhoneNumber, String message) {
        log.info("PhoneMessageGateway fallback WHATSAPP to={} messageLength={}", maskPhone(toPhoneNumber), messageLength(message));
        return new PhoneMessageDispatchResult(true, "fallback:whatsapp:accepted");
    }

    private String maskPhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "***";
        }
        int keep = Math.min(4, phoneNumber.length());
        return "*".repeat(phoneNumber.length() - keep) + phoneNumber.substring(phoneNumber.length() - keep);
    }

    private int messageLength(String message) {
        return message == null ? 0 : message.length();
    }
}
