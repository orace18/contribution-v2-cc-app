package com.ccapp.notifications.dispatcher;

public record DispatchResult(
        boolean success,
        String providerResponse
) {
}
