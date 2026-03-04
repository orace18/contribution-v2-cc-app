package com.ccapp.common.api;

import java.time.Instant;

public record CommonResponse<T>(
        boolean success,
        String message,
        T data,
        Instant timestamp
) {
    public static <T> CommonResponse<T> success(String message, T data) {
        return new CommonResponse<>(true, message, data, Instant.now());
    }

    public static CommonResponse<Void> success(String message) {
        return new CommonResponse<>(true, message, null, Instant.now());
    }

    public static CommonResponse<Void> error(String message) {
        return new CommonResponse<>(false, message, null, Instant.now());
    }
}
