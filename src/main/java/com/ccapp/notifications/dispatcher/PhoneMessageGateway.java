package com.ccapp.notifications.dispatcher;

public interface PhoneMessageGateway {

    PhoneMessageDispatchResult sendSms(String toPhoneNumber, String message);

    PhoneMessageDispatchResult sendWhatsApp(String toPhoneNumber, String message);
}
