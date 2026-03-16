package com.ccapp.notifications.dispatcher;

public record PhoneMessageDispatchResult(
        boolean success,
        String providerResponse
) {
}
