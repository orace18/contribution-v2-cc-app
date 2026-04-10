package com.ccapp.config.security;

import com.ccapp.common.api.ApiEnvelope;
import com.ccapp.common.api.CommonResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
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
            return "Unauthorized admin access";
        }
        return "Unauthorized";
    }
}
