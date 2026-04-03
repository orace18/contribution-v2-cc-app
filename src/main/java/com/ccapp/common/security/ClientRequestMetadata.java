package com.ccapp.common.security;

public record ClientRequestMetadata(
        String ipAddress,
        String countryCode,
        String deviceIdentifier
) {
}
