package com.ccapp.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class HttpRouteLoggingFilter extends OncePerRequestFilter {

    private static final String NO_VALUE = "";

    @Value("${observability.http.trace.enabled:true}")
    private boolean enabled;

    @Value("${observability.http.trace.max-payload-length:4000}")
    private int maxPayloadLength;

    @Value("${observability.http.trace.log-authorization-token:false}")
    private boolean logAuthorizationToken;

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
        String authorization = logAuthorizationToken
                ? safeValue(request.getHeader("Authorization"))
                : formatCredentialPresence(request.getHeader("Authorization"));
        String apiKey = formatCredentialPresence(request.getHeader("X-API-KEY"));
        String error = chainError == null ? NO_VALUE : chainError.getClass().getSimpleName() + ":" + chainError.getMessage();
        int status = response.getStatus();
        if (chainError != null && status < 400) {
            status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }

        log.info(
                "HTTP route={} method={} status={} durationMs={} remoteIp={} authorization={} xApiKey={} requestContentType={} requestBytes={} responseContentType={} responseBytes={} error={}",
                route,
                request.getMethod(),
                status,
                durationMs,
                request.getRemoteAddr(),
                authorization,
                apiKey,
                safeValue(request.getContentType()),
                request.getContentAsByteArray().length,
                safeValue(response.getContentType()),
                response.getContentAsByteArray().length,
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

    private String formatCredentialPresence(String value) {
        return StringUtils.hasText(value) ? "present" : NO_VALUE;
    }

    private String safeValue(String value) {
        return StringUtils.hasText(value) ? value : NO_VALUE;
    }
}
