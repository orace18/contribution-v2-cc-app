package com.ccapp.config.security;

import com.ccapp.common.api.ApiEnvelope;
import com.ccapp.common.api.CommonResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (request.getServletPath().startsWith("/api/mobile/")) {
            objectMapper.writeValue(response.getOutputStream(), ApiEnvelope.error(messageFor(request)));
            return;
        }
        objectMapper.writeValue(response.getOutputStream(), CommonResponse.error(messageFor(request)));
    }

    private String messageFor(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path.startsWith("/api/admin/") || path.startsWith("/admin/") || path.startsWith("/dev-tools/")) {
            return "Forbidden admin access";
        }
        return "Access denied";
    }
}
