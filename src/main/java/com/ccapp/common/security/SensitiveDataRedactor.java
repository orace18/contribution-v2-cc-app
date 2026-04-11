package com.ccapp.common.security;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SensitiveDataRedactor {

    public static final String REDACTED = "[REDACTED]";
    public static final String REDACTED_BEARER = "Bearer " + REDACTED;

    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile(
            "(?i)(\"?[A-Za-z0-9_.\\-]+\"?\\s*[:=]\\s*)(\"[^\"]*\"|'[^']*'|Bearer\\s+[^,\\s\\]}&]+|[^,\\s\\]}&]+)"
    );

    private SensitiveDataRedactor() {
    }

    public static String redactAuthorizationHeader(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return REDACTED_BEARER;
    }

    public static String redactSecretValue(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return REDACTED;
    }

    public static String redactMessage(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        Matcher matcher = KEY_VALUE_PATTERN.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String prefix = matcher.group(1);
            String rawKey = extractKey(prefix);
            String replacementValue = isAuthorizationKey(rawKey) ? REDACTED_BEARER : shouldRedactKey(rawKey) ? REDACTED : matcher.group(2);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(prefix + replacementValue));
        }
        matcher.appendTail(buffer);
        return BEARER_PATTERN.matcher(buffer.toString()).replaceAll(REDACTED_BEARER);
    }

    public static String redactThrowable(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        String message = redactMessage(throwable.getMessage());
        return throwable.getClass().getSimpleName() + (StringUtils.hasText(message) ? ":" + message : "");
    }

    public static String redactRoute(String requestUri, String queryString) {
        String route = StringUtils.hasText(requestUri) ? requestUri : "";
        if (!StringUtils.hasText(queryString)) {
            return route;
        }
        return route + "?" + redactQueryString(queryString);
    }

    public static String redactQueryString(String queryString) {
        if (!StringUtils.hasText(queryString)) {
            return "";
        }

        List<String> parts = new ArrayList<>();
        for (String pair : queryString.split("&")) {
            int separator = pair.indexOf('=');
            if (separator < 0) {
                parts.add(pair);
                continue;
            }
            String key = pair.substring(0, separator);
            String value = pair.substring(separator + 1);
            parts.add(key + "=" + (shouldRedactKey(key) ? REDACTED : redactMessage(value)));
        }
        return String.join("&", parts);
    }

    private static String extractKey(String prefix) {
        String normalized = prefix.replace("\"", "");
        int colonIndex = normalized.indexOf(':');
        int equalsIndex = normalized.indexOf('=');
        int separator = colonIndex >= 0 ? colonIndex : equalsIndex;
        String key = separator >= 0 ? normalized.substring(0, separator) : normalized;
        return key.trim();
    }

    private static boolean shouldRedactKey(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }

        String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return isAuthorizationKey(normalized)
                || normalized.equals("token")
                || normalized.endsWith("token")
                || normalized.equals("pin")
                || normalized.endsWith("pin")
                || normalized.equals("otp")
                || normalized.endsWith("otp")
                || normalized.endsWith("otpcode")
                || normalized.contains("secret")
                || normalized.contains("apikey")
                || normalized.contains("signature")
                || normalized.contains("credential");
    }

    private static boolean isAuthorizationKey(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return normalized.equals("authorization");
    }
}
