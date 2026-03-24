package com.ccapp.common.api;

import java.time.Instant;

public record ApiEnvelope<T>(
        boolean success,
        String message,
        T data,
        Instant timestamp
) {
    public static <T> ApiEnvelope<T> success(String message, T data) {
        return new ApiEnvelope<>(true, message, data, Instant.now());
    }

    public static ApiEnvelope<Void> success(String message) {
        return new ApiEnvelope<>(true, message, null, Instant.now());
    }

    public static ApiEnvelope<Void> error(String message) {
        return new ApiEnvelope<>(false, message, null, Instant.now());
    }
}
