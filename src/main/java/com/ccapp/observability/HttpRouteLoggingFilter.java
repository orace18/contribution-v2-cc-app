package com.ccapp.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class HttpRouteLoggingFilter extends OncePerRequestFilter {

    private static final String NO_VALUE = "";

    @Value("${observability.http.trace.enabled:true}")
    private boolean enabled;

    @Value("${observability.http.trace.max-payload-length:4000}")
    private int maxPayloadLength;

    @Value("${observability.http.trace.log-auth-token:true}")
    private boolean logAuthToken;

    @Value("${observability.http.trace.log-api-key:true}")
    private boolean logApiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        long startedAt = System.currentTimeMillis();
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request, maxPayloadLength);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        Exception chainError = null;

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } catch (Exception ex) {
            chainError = ex;
            throw ex;
        } finally {
            try {
                logExchange(wrappedRequest, wrappedResponse, startedAt, chainError);
            } finally {
                wrappedResponse.copyBodyToResponse();
            }
        }
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return true;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return true;
    }

    private void logExchange(ContentCachingRequestWrapper request,
                             ContentCachingResponseWrapper response,
                             long startedAt,
                             Exception chainError) {
        long durationMs = System.currentTimeMillis() - startedAt;
        String route = formatRoute(request);
        String requestBody = formatBody(request.getContentAsByteArray(), request.getCharacterEncoding(), request.getContentType());
        String responseBody = formatBody(response.getContentAsByteArray(), response.getCharacterEncoding(), response.getContentType());
        String authorization = formatAuthorization(request.getHeader("Authorization"));
        String apiKey = formatApiKey(request.getHeader("X-API-KEY"));
        String error = chainError == null ? NO_VALUE : chainError.getClass().getSimpleName() + ":" + chainError.getMessage();
        int status = response.getStatus();
        if (chainError != null && status < 400) {
            status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }

        log.info(
                "HTTP route={} method={} status={} durationMs={} remoteIp={} authorization={} xApiKey={} requestBody={} responseBody={} error={}",
                route,
                request.getMethod(),
                status,
                durationMs,
                request.getRemoteAddr(),
                authorization,
                apiKey,
                requestBody,
                responseBody,
                error
        );
    }

    private String formatRoute(HttpServletRequest request) {
        String queryString = request.getQueryString();
        if (!StringUtils.hasText(queryString)) {
            return request.getRequestURI();
        }
        return request.getRequestURI() + "?" + queryString;
    }

    private String formatAuthorization(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)) {
            return NO_VALUE;
        }
        if (logAuthToken) {
            return authorizationHeader;
        }
        if (authorizationHeader.startsWith("Bearer ")) {
            return "Bearer " + maskToken(authorizationHeader.substring(7));
        }
        return maskToken(authorizationHeader);
    }

    private String formatApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return NO_VALUE;
        }
        if (logApiKey) {
            return apiKey;
        }
        return maskToken(apiKey);
    }

    private String maskToken(String token) {
        if (!StringUtils.hasText(token) || token.length() <= 4) {
            return "****";
        }
        return token.substring(0, 2) + "****" + token.substring(token.length() - 2);
    }

    private String formatBody(byte[] content, String encoding, String contentType) {
        if (content == null || content.length == 0) {
            return NO_VALUE;
        }
        if (!isVisibleContent(contentType)) {
            return "[omitted " + content.length + " bytes; contentType=" + contentType + "]";
        }
        Charset charset = resolveCharset(encoding);
        String payload = new String(content, charset).trim();
        if (payload.isEmpty()) {
            return NO_VALUE;
        }
        if (payload.length() > maxPayloadLength) {
            return payload.substring(0, maxPayloadLength) + "...(truncated)";
        }
        return payload;
    }

    private Charset resolveCharset(String encoding) {
        if (!StringUtils.hasText(encoding)) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding);
        } catch (Exception ignored) {
            return StandardCharsets.UTF_8;
        }
    }

    private boolean isVisibleContent(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return true;
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            if ("text".equalsIgnoreCase(mediaType.getType())) {
                return true;
            }
            String subtype = mediaType.getSubtype();
            if (!StringUtils.hasText(subtype)) {
                return false;
            }
            String normalizedSubtype = subtype.toLowerCase();
            return normalizedSubtype.contains("json")
                    || normalizedSubtype.contains("xml")
                    || normalizedSubtype.contains("x-www-form-urlencoded")
                    || normalizedSubtype.contains("javascript")
                    || normalizedSubtype.contains("yaml");
        } catch (InvalidMediaTypeException ignored) {
            return true;
        }
    }
}
