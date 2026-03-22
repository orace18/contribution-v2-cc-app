package com.ccapp.config.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RestSecurityHandlersTest {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void restAuthenticationEntryPoint_shouldReturnJsonForAdminRoutes() throws Exception {
        RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/country-config/configured-countries");
        request.setServletPath("/api/admin/country-config/configured-countries");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new InsufficientAuthenticationException("Missing token"));

        Map<String, Object> payload = objectMapper.readValue(response.getContentAsString(), MAP_TYPE);
        assertEquals(401, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertEquals(false, payload.get("success"));
        assertEquals("Unauthorized admin access", payload.get("message"));
    }

    @Test
    void restAccessDeniedHandler_shouldReturnJsonForAdminRoutes() throws Exception {
        RestAccessDeniedHandler handler = new RestAccessDeniedHandler(objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/users");
        request.setServletPath("/api/admin/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("Denied"));

        Map<String, Object> payload = objectMapper.readValue(response.getContentAsString(), MAP_TYPE);
        assertEquals(403, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertEquals(false, payload.get("success"));
        assertEquals("Forbidden admin access", payload.get("message"));
    }
}
