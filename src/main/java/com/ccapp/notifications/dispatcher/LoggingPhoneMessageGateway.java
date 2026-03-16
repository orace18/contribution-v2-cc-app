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
        log.info("PhoneMessageGateway fallback SMS to={} message={}", toPhoneNumber, message);
        return new PhoneMessageDispatchResult(true, "fallback:sms:accepted");
    }

    @Override
    public PhoneMessageDispatchResult sendWhatsApp(String toPhoneNumber, String message) {
        log.info("PhoneMessageGateway fallback WHATSAPP to={} message={}", toPhoneNumber, message);
        return new PhoneMessageDispatchResult(true, "fallback:whatsapp:accepted");
    }
}
