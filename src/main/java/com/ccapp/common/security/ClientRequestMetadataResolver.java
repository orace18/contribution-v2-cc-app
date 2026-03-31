package com.ccapp.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class ClientRequestMetadataResolver {

    public ClientRequestMetadata current() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return new ClientRequestMetadata(null, null);
        }
        return resolve(attributes.getRequest());
    }

    public ClientRequestMetadata resolve(HttpServletRequest request) {
        if (request == null) {
            return new ClientRequestMetadata(null, null);
        }
        return new ClientRequestMetadata(resolveIpAddress(request), resolveCountryCode(request));
    }

    private String resolveIpAddress(HttpServletRequest request) {
        String forwardedFor = firstHeaderValue(request, "X-Forwarded-For");
        if (forwardedFor != null) {
            String[] ips = forwardedFor.split(",");
            if (ips.length > 0 && !ips[0].isBlank()) {
                return ips[0].trim();
            }
        }
        String realIp = firstHeaderValue(request, "X-Real-IP");
        if (realIp != null) {
            return realIp;
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr == null || remoteAddr.isBlank() ? null : remoteAddr.trim();
    }

    private String resolveCountryCode(HttpServletRequest request) {
        String candidate = firstHeaderValue(request, "CF-IPCountry");
        if (candidate == null) {
            candidate = firstHeaderValue(request, "X-Country-Code");
        }
        if (candidate == null) {
            candidate = firstHeaderValue(request, "X-App-Country");
        }
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        return candidate.trim().toUpperCase();
    }

    private String firstHeaderValue(HttpServletRequest request, String headerName) {
        String value = request.getHeader(headerName);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
